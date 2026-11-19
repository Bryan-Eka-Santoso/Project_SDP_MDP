package id.sch.santaagnes.sim.ui.admin.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.AdminJournalDto
import id.sch.santaagnes.sim.data.repository.AdminJournalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

data class AdminJournalState(
    val items: List<AdminJournalDto> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) {
    val canLoadMore get() = !loading && page > 0 && page < totalPages
}

@HiltViewModel
class AdminJournalViewModel @Inject constructor(
    private val repository: AdminJournalRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AdminJournalState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init { refresh() }

    fun setStart(date: LocalDate?) {
        if (date != null && mutable.value.endDate != null && date.isAfter(mutable.value.endDate)) return
        mutable.value = mutable.value.copy(startDate = date)
        refresh()
    }

    fun setEnd(date: LocalDate?) {
        if (date != null && mutable.value.startDate != null && date.isBefore(mutable.value.startDate)) return
        mutable.value = mutable.value.copy(endDate = date)
        refresh()
    }

    fun clearDates() {
        mutable.value = mutable.value.copy(startDate = null, endDate = null)
        refresh()
    }

    fun refresh() { fetch(1) }
    fun nextPage() { if (mutable.value.canLoadMore) fetch(mutable.value.page + 1) }

    private fun fetch(nextPage: Int) {
        job?.cancel()
        job = viewModelScope.launch {
            val old = mutable.value
            mutable.value = if (nextPage == 1) old.copy(items = emptyList(), page = 0, totalPages = 0, total = 0, loading = true, error = null)
                            else old.copy(loading = true, error = null)
            try {
                val result = repository.getPage(nextPage, old.startDate?.toString(), old.endDate?.toString())
                val items = if (nextPage == 1) result.rows else mutable.value.items + result.rows
                mutable.value = mutable.value.copy(
                    items = items.distinctBy { it.id }, page = result.page,
                    totalPages = result.totalPages, total = result.total, loading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is IOException -> "Tidak dapat menghubungi server."
                    is HttpException -> when (e.code()) {
                        401 -> "Sesi login habis. Silakan login ulang."
                        403 -> "Halaman ini hanya dapat diakses oleh Admin."
                        else -> "Gagal memuat jurnal (HTTP ${e.code()})."
                    }
                    else -> e.message ?: "Terjadi kesalahan saat memuat jurnal."
                }
                mutable.value = mutable.value.copy(loading = false, error = message)
            }
        }
    }
}
