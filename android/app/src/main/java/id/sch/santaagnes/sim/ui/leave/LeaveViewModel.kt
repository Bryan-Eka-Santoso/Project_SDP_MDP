package id.sch.santaagnes.sim.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.repository.LeaveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaveViewModel @Inject constructor(
    private val repository: LeaveRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(LeaveUiState())
    val uiState = mutable.asStateFlow()

    fun loadChildren() = viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, error = null)
        try {
            val children = repository.children()
            val requests = children.firstOrNull()?.let { repository.list(it.id) }.orEmpty()
            mutable.value = mutable.value.copy(loading = false, children = children, requests = requests)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(loading = false, error = LeaveRepository.message(e))
        }
    }

    fun loadRequests(childId: Int) = viewModelScope.launch {
        mutable.value = mutable.value.copy(loading = true, error = null, message = null)
        try {
            mutable.value = mutable.value.copy(loading = false, requests = repository.list(childId))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(loading = false, error = LeaveRepository.message(e))
        }
    }

    fun create(childId: Int, start: String, end: String, reason: String) = viewModelScope.launch {
        if (mutable.value.saving) return@launch
        mutable.value = mutable.value.copy(saving = true, error = null, message = null)
        try {
            repository.create(childId, start, end, reason)
            val requests = repository.list(childId)
            mutable.value = mutable.value.copy(
                saving = false, requests = requests,
                message = "Pengajuan izin berhasil dikirim. Status: Menunggu persetujuan."
            )
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.value = mutable.value.copy(saving = false, error = LeaveRepository.message(e))
        }
    }
}
