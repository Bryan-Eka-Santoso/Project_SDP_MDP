package id.sch.santaagnes.sim.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.repository.AttendanceRepository
import id.sch.santaagnes.sim.data.repository.GradesRepository
import id.sch.santaagnes.sim.domain.model.AttendanceRecapRow
import id.sch.santaagnes.sim.domain.model.GradeAssignment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttendanceRecapState(
    val loading: Boolean = false,
    val assignments: List<GradeAssignment> = emptyList(),
    val selectedId: Int? = null,
    /** null = tanpa batas (seluruh semester aktif). Format yyyy-MM-dd. */
    val startDate: String? = null,
    val endDate: String? = null,
    val rows: List<AttendanceRecapRow> = emptyList(),
    val error: String? = null
)

/** Rekap per siswa untuk pengajar dan wali kelas (GET /teaching-assignments/:id/attendance-summary). */
@HiltViewModel
class TeacherAttendanceRecapViewModel @Inject constructor(
    private val attendance: AttendanceRepository,
    private val grades: GradesRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AttendanceRecapState())
    val state = mutable.asStateFlow()
    private var request: Job? = null

    fun start(assignmentId: Int, homeroomOnly: Boolean) {
        if (mutable.value.assignments.isNotEmpty()) return
        request?.cancel()
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                val all = grades.assignments(includeHomeroom = true)
                val options = if (homeroomOnly) all.filter { it.isHomeroomClass } else all
                val selected = options.firstOrNull { it.id == assignmentId } ?: options.firstOrNull()
                mutable.value = mutable.value.copy(loading = false, assignments = options, selectedId = selected?.id)
                if (selected != null) load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = AttendanceRepository.errorMessage(e))
            }
        }
    }

    fun select(assignmentId: Int) {
        if (assignmentId == mutable.value.selectedId) return
        mutable.value = mutable.value.copy(selectedId = assignmentId)
        load()
    }

    fun setPeriod(startDate: String?, endDate: String?) {
        mutable.value = mutable.value.copy(startDate = startDate, endDate = endDate)
        load()
    }

    fun load() {
        val current = mutable.value
        val assignmentId = current.selectedId ?: return
        request?.cancel()
        if (current.startDate != null && current.endDate != null && current.startDate > current.endDate) {
            mutable.value = current.copy(
                loading = false,
                rows = emptyList(),
                error = "Tanggal awal tidak boleh setelah tanggal akhir."
            )
            return
        }
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, rows = emptyList(), error = null)
            try {
                val rows = attendance.classRecap(assignmentId, current.startDate, current.endDate)
                mutable.value = mutable.value.copy(loading = false, rows = rows)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = AttendanceRepository.errorMessage(e))
            }
        }
    }
}
