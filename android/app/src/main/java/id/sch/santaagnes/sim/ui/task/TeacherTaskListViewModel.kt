package id.sch.santaagnes.sim.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.TaskRepository
import id.sch.santaagnes.sim.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TeacherTaskListUiState(
    val tasks: List<Task> = emptyList(),
    // muat halaman pertama (daftar lama tetap tampil selama itu)
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && !isLoading && errorMessage == null
}

@HiltViewModel
class TeacherTaskListViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherTaskListUiState())
    val uiState: StateFlow<TeacherTaskListUiState> = _uiState.asStateFlow()

    private var nextPage = 1

    /** Muat ulang dari halaman pertama; dipanggil saat layar tampil (termasuk setelah kembali dari formulir). */
    fun refresh() {
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.tasks(1)) {
                is Resource.Success -> {
                    nextPage = 2
                    _uiState.update {
                        it.copy(
                            tasks = result.data.tasks,
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            when (val result = repository.tasks(nextPage)) {
                is Resource.Success -> {
                    nextPage += 1
                    _uiState.update { current ->
                        // Penyaring id mencegah ganda bila daftar bergeser di server di antara dua halaman.
                        val known = current.tasks.map { it.id }.toSet()
                        current.copy(
                            tasks = current.tasks + result.data.tasks.filter { it.id !in known },
                            hasMore = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoadingMore = false, errorMessage = result.message) }
            }
        }
    }

    fun delete(taskId: Int) {
        viewModelScope.launch {
            when (val result = repository.delete(taskId)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(tasks = state.tasks.filterNot { it.id == taskId }, errorMessage = null)
                }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
