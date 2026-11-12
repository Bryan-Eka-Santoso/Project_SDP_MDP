package id.sch.santaagnes.sim.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.BillRepository
import id.sch.santaagnes.sim.domain.model.Bill
import id.sch.santaagnes.sim.domain.model.BillFilter
import id.sch.santaagnes.sim.domain.model.BillScope
import id.sch.santaagnes.sim.domain.model.BillScopeOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillListUiState(
    // null sampai data sesi terbaca
    val role: String? = null,
    val options: List<BillScopeOption> = emptyList(),
    val selectedOption: Int = 0,
    val filter: BillFilter = BillFilter.ALL,
    val query: String = "",
    val bills: List<Bill> = emptyList(),
    // muat halaman pertama (daftar lama tetap tampil selama itu)
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
) {
    private val scope: BillScope? get() = options.getOrNull(selectedOption)?.scope

    /** Akun ini tidak punya tagihan yang bisa dibuka (mis. wali kelas tanpa kelas binaan). */
    val hasNoAccess: Boolean get() = role != null && options.isEmpty()

    /** Daftar lintas siswa memperlihatkan siswa pemilik tiap tagihan dan bisa dicari. */
    val showStudent: Boolean get() = scope != null && scope !is BillScope.Student

    val canImport: Boolean get() = role == "ADMIN"

    val isEmpty: Boolean get() = bills.isEmpty() && !isLoading && errorMessage == null && !hasNoAccess
}

@HiltViewModel
class BillListViewModel @Inject constructor(
    private val repository: BillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillListUiState())
    val uiState: StateFlow<BillListUiState> = _uiState.asStateFlow()

    private var nextPage = 1

    init {
        viewModelScope.launch {
            val viewer = repository.viewer()
            _uiState.update {
                it.copy(role = viewer.role, options = viewer.options, selectedOption = viewer.initialIndex)
            }
            if (viewer.options.isEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
            } else {
                refresh()
            }
        }
    }

    /** Muat ulang dari halaman pertama; dipanggil saat layar tampil (termasuk setelah impor). */
    fun refresh() {
        val state = _uiState.value
        val scope = state.options.getOrNull(state.selectedOption)?.scope ?: return
        if (state.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.bills(scope, 1, state.filter, state.query)) {
                is Resource.Success -> {
                    // Pilihan diganti selagi memuat: hasil lama dibuang.
                    if (isStale(scope, state)) return@launch
                    nextPage = 2
                    _uiState.update {
                        it.copy(bills = result.data.items, hasMore = result.data.hasMore, isLoading = false)
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val scope = state.options.getOrNull(state.selectedOption)?.scope ?: return
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            when (val result = repository.bills(scope, nextPage, state.filter, state.query)) {
                is Resource.Success -> {
                    if (isStale(scope, state)) return@launch
                    nextPage += 1
                    _uiState.update { current ->
                        // Penyaring id mencegah ganda bila daftar bergeser di server di antara dua halaman.
                        val known = current.bills.map { it.id }.toSet()
                        current.copy(
                            bills = current.bills + result.data.items.filter { it.id !in known },
                            hasMore = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoadingMore = false, errorMessage = result.message) }
            }
        }
    }

    fun selectOption(index: Int) {
        val state = _uiState.value
        if (index !in state.options.indices || index == state.selectedOption) return
        _uiState.update { it.copy(selectedOption = index, bills = emptyList(), hasMore = false) }
        viewModelScope.launch { repository.remember(state.options[index].scope) }
        refresh()
    }

    fun selectFilter(filter: BillFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        refresh()
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed == _uiState.value.query) return
        _uiState.update { it.copy(query = trimmed) }
        refresh()
    }

    // Lingkup, penyaring, atau pencarian sudah berganti sejak permintaan ini dikirim.
    private fun isStale(scope: BillScope, requested: BillListUiState): Boolean {
        val now = _uiState.value
        return now.options.getOrNull(now.selectedOption)?.scope != scope ||
            now.filter != requested.filter || now.query != requested.query
    }
}
