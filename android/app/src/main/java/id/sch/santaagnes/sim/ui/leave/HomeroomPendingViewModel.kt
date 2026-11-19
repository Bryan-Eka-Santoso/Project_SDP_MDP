package id.sch.santaagnes.sim.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.HomeroomLeaveDto
import id.sch.santaagnes.sim.data.repository.HomeroomLeaveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeroomPendingState(
    val items: List<HomeroomLeaveDto> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val decisionBusyId: Int? = null,
    val notice: String? = null
) {
    val hasMore get() = page > 0 && items.size < total
}

@HiltViewModel
class HomeroomPendingViewModel @Inject constructor(
    private val repository: HomeroomLeaveRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(HomeroomPendingState())
    val state = mutable.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (mutable.value.loading || mutable.value.loadingMore) return
        viewModelScope.launch {
            mutable.value = HomeroomPendingState(loading = true)
            try {
                val result = repository.pending(1)
                mutable.value = HomeroomPendingState(
                    items = result.items,
                    page = 1,
                    total = result.meta?.total ?: result.items.size
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = HomeroomPendingState(error = HomeroomLeaveRepository.friendlyError(e))
            }
        }
    }

    fun decide(item: HomeroomLeaveDto, approved: Boolean) {
        val before = mutable.value
        if (before.loading || before.loadingMore || before.decisionBusyId != null) return
        if (before.items.none { it.id == item.id }) return
        viewModelScope.launch {
            mutable.value = before.copy(decisionBusyId = item.id, error = null, notice = null)
            try {
                repository.decide(item.id, if (approved) "APPROVED" else "REJECTED")
                // Ambil ulang daftar dari server supaya total, pagination, dan status akurat.
                val latest = repository.pending(1)
                mutable.value = HomeroomPendingState(
                    items = latest.items,
                    page = 1,
                    total = latest.meta?.total ?: latest.items.size,
                    notice = if (approved) "Izin ${item.studentName} disetujui." else "Izin ${item.studentName} ditolak."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    decisionBusyId = null,
                    error = HomeroomLeaveRepository.friendlyError(e)
                )
            }
        }
    }

    fun loadMore() {
        val before = mutable.value
        if (before.loading || before.loadingMore || !before.hasMore) return
        viewModelScope.launch {
            mutable.value = before.copy(loadingMore = true, error = null)
            try {
                val result = repository.pending(before.page + 1)
                mutable.value = mutable.value.copy(
                    items = before.items + result.items,
                    page = before.page + 1,
                    total = result.meta?.total ?: before.total,
                    loadingMore = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    loadingMore = false,
                    error = HomeroomLeaveRepository.friendlyError(e)
                )
            }
        }
    }
}
