package id.sch.santaagnes.sim.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.repository.AttendanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val repository: AttendanceRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AttendanceUiState())
    val uiState = mutable.asStateFlow()

    fun loadSlots(assignmentId: Int) = viewModelScope.launch {
        mutable.value = AttendanceUiState(loading = true)
        try {
            mutable.value = AttendanceUiState(slots = repository.slots(assignmentId))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.value = AttendanceUiState(error = AttendanceRepository.errorMessage(e))
        }
    }

    fun loadSession(slotId: Int, date: String) = viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, session = null, message = null, error = null)
        try {
            val data = repository.session(slotId, date)
            mutable.value = mutable.value.copy(loading = false, session = data)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(loading = false, error = AttendanceRepository.errorMessage(e))
        }
    }

    fun save(slotId: Int, date: String, journal: String, statuses: Map<Int, String>) = viewModelScope.launch {
        if (mutable.value.saving) return@launch
        mutable.value = mutable.value.copy(saving = true, error = null, message = null)
        try {
            repository.save(slotId, date, journal, statuses)
            val refreshed = repository.session(slotId, date)
            mutable.value = mutable.value.copy(saving = false, session = refreshed, message = "Presensi dan jurnal berhasil disimpan.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(saving = false, error = AttendanceRepository.errorMessage(e))
        }
    }
}
