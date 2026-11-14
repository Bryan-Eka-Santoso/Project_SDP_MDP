package id.sch.santaagnes.sim.ui.admin.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepository
import id.sch.santaagnes.sim.domain.model.AdminChange
import id.sch.santaagnes.sim.domain.model.ChangeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangeListUiState(
    // null = semua jenis perubahan
    val type: ChangeType? = null,
    val changes: List<AdminChange> = emptyList(),
    // muat halaman pertama (daftar lama tetap tampil selama itu)
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = changes.isEmpty() && !isLoading && errorMessage == null
}

@HiltViewModel
class ChangeListViewModel @Inject constructor(
    private val repository: AdminScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeListUiState())
    val uiState: StateFlow<ChangeListUiState> = _uiState.asStateFlow()

    private var nextPage = 1

    /** Muat ulang dari halaman pertama; dipanggil saat layar tampil (termasuk setelah kembali dari formulir). */
    fun refresh() {
        if (_uiState.value.isLoadingMore) return
        val type = _uiState.value.type
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.changes(1, type)) {
                is Resource.Success -> {
                    // Jenis diganti selagi memuat: hasil lama dibuang.
                    if (_uiState.value.type != type) return@launch
                    nextPage = 2
                    _uiState.update {
                        it.copy(changes = result.data.items, hasMore = result.data.hasMore, isLoading = false)
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            when (val result = repository.changes(nextPage, state.type)) {
                is Resource.Success -> {
                    if (_uiState.value.type != state.type) return@launch
                    nextPage += 1
                    _uiState.update { current ->
                        val known = current.changes.map { it.id }.toSet()
                        current.copy(
                            changes = current.changes + result.data.items.filter { it.id !in known },
                            hasMore = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoadingMore = false, errorMessage = result.message) }
            }
        }
    }

    fun selectType(type: ChangeType?) {
        if (type == _uiState.value.type) return
        _uiState.update { it.copy(type = type) }
        refresh()
    }

    fun delete(changeId: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteChange(changeId)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(changes = state.changes.filterNot { it.id == changeId }, errorMessage = null)
                }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
