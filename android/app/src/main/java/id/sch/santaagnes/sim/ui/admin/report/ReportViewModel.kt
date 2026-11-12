package id.sch.santaagnes.sim.ui.admin.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.domain.model.ReportOptions
import id.sch.santaagnes.sim.domain.model.ReportPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Status layar laporan: [R] = baris laporan, [F] = filter laporan itu. */
data class ReportUiState<R, F>(
    val filter: F,
    val rows: List<R> = emptyList(),
    // jumlah seluruh baris sesuai filter (bukan hanya yang sudah dimuat)
    val total: Int = 0,
    val options: ReportOptions = ReportOptions(),
    // muat halaman pertama (daftar lama tetap tampil selama itu)
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isExporting: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = rows.isEmpty() && !isLoading && errorMessage == null
}

/** Kejadian sekali jalan hasil ekspor Excel (F-LAP-11). */
sealed interface ReportEvent {
    data object ExportSaved : ReportEvent
    data object ExportWriteFailed : ReportEvent
    data class ExportFailed(val message: String) : ReportEvent
}

/**
 * Kerangka bersama layar laporan admin: memuat halaman, filter, dan ekspor. Laporan baru cukup
 * menurunkan kelas ini dan mengisi empat fungsi abstrak.
 */
abstract class ReportViewModel<R : Any, F : Any>(initialFilter: F) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState<R, F>(filter = initialFilter))
    val uiState: StateFlow<ReportUiState<R, F>> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReportEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReportEvent> = _events.asSharedFlow()

    private var nextPage = 1
    private var loadJob: Job? = null
    private var optionsRequested = false

    protected abstract suspend fun fetch(page: Int, filter: F): Resource<ReportPage<R>>

    protected abstract suspend fun export(filter: F, destination: String): Resource<Boolean>

    protected abstract suspend fun fetchOptions(): Resource<ReportOptions>

    /** Kunci baris, untuk membuang duplikat bila data bergeser antar halaman. */
    protected abstract fun idOf(row: R): Int

    /** Muat ulang dari halaman pertama; dipanggil saat layar tampil. */
    fun refresh() {
        loadJob?.cancel()
        val filter = _uiState.value.filter
        loadOptionsOnce()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isLoadingMore = false, errorMessage = null) }
            when (val result = fetch(1, filter)) {
                is Resource.Success -> {
                    nextPage = 2
                    _uiState.update {
                        it.copy(
                            rows = result.data.items,
                            total = result.data.total,
                            hasMore = result.data.hasMore,
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            when (val result = fetch(nextPage, state.filter)) {
                is Resource.Success -> {
                    nextPage += 1
                    _uiState.update { current ->
                        val known = current.rows.map(::idOf).toSet()
                        current.copy(
                            rows = current.rows + result.data.items.filter { idOf(it) !in known },
                            total = result.data.total,
                            hasMore = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoadingMore = false, errorMessage = result.message) }
            }
        }
    }

    /** Ekspor data yang sedang tampil (filter yang sama) ke [destination] (uri teks dari pemilih file). */
    fun exportTo(destination: String) {
        val state = _uiState.value
        if (state.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val event = when (val result = export(state.filter, destination)) {
                is Resource.Success -> if (result.data) ReportEvent.ExportSaved else ReportEvent.ExportWriteFailed
                is Resource.Error -> ReportEvent.ExportFailed(result.message)
            }
            _uiState.update { it.copy(isExporting = false) }
            _events.emit(event)
        }
    }

    /** Ubah filter lalu muat ulang; tidak melakukan apa-apa bila hasilnya sama. */
    protected fun updateFilter(transform: (F) -> F) {
        val current = _uiState.value.filter
        val changed = transform(current)
        if (changed == current) return
        // Daftar lama dikosongkan: bila pemuatan gagal, tidak ada baris dari filter sebelumnya yang tersisa.
        _uiState.update {
            it.copy(filter = changed, rows = emptyList(), total = 0, hasMore = false, isLoading = true)
        }
        refresh()
    }

    // Berjalan terpisah dari pemuatan daftar supaya daftar tidak menunggu dan tidak ikut dibatalkan.
    private fun loadOptionsOnce() {
        if (optionsRequested) return
        optionsRequested = true
        viewModelScope.launch {
            // Pilihan filter hanya pelengkap: bila gagal, daftar tetap dimuat dan dicoba lagi saat muat ulang.
            when (val result = fetchOptions()) {
                is Resource.Success -> _uiState.update { it.copy(options = result.data) }
                is Resource.Error -> optionsRequested = false
            }
        }
    }
}
