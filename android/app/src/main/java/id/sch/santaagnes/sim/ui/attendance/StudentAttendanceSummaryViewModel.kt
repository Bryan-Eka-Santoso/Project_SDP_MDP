package id.sch.santaagnes.sim.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.remote.AttendanceSummaryDto
import id.sch.santaagnes.sim.data.repository.AttendanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class StudentAttendanceSummaryState(
    val month: YearMonth = YearMonth.from(DateTimeWib.today()),
    val loading: Boolean = false,
    val summary: AttendanceSummaryDto? = null,
    val error: String? = null
)

@HiltViewModel
class StudentAttendanceSummaryViewModel @Inject constructor(
    private val repository: AttendanceRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(StudentAttendanceSummaryState())
    val state = mutable.asStateFlow()
    private var request: Job? = null

    init { loadMonth() }

    fun previousMonth() = changeMonth(-1)
    fun nextMonth() = changeMonth(1)
    fun retry() = loadMonth()

    private fun changeMonth(delta: Long) {
        mutable.value = mutable.value.copy(month = mutable.value.month.plusMonths(delta))
        loadMonth()
    }

    private fun loadMonth() {
        request?.cancel()
        val month = mutable.value.month
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, summary = null, error = null)
            try {
                val result = repository.mySummary(
                    month.atDay(1).toString(),
                    month.atEndOfMonth().toString()
                )
                mutable.value = mutable.value.copy(loading = false, summary = result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    loading = false,
                    error = AttendanceRepository.errorMessage(e)
                )
            }
        }
    }
}
