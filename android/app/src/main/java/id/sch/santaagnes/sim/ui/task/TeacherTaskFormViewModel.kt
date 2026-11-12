package id.sch.santaagnes.sim.ui.task

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.TaskRepository
import id.sch.santaagnes.sim.domain.model.TaskInput
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TeacherTaskFormUiState(
    val assignments: List<TeachingAssignment> = emptyList(),
    val isLoadingAssignments: Boolean = true,
    val isSaving: Boolean = false,
    // galat dari server (jaringan, validasi, dll.); galat per field ada di *Error di bawah
    val errorMessage: String? = null,
    @param:StringRes val assignmentError: Int? = null,
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val descriptionError: Int? = null,
    @param:StringRes val dueDateError: Int? = null
)

@HiltViewModel
class TeacherTaskFormViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeacherTaskFormUiState())
    val uiState: StateFlow<TeacherTaskFormUiState> = _uiState.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Dipancarkan sekali setelah tugas tersimpan di server. */
    val saved = savedChannel.receiveAsFlow()

    /** Daftar kelas + mapel hanya dibutuhkan saat membuat tugas baru. */
    fun loadAssignments() {
        // Sudah termuat (mis. layar diputar): tidak perlu mengambil ulang.
        if (_uiState.value.assignments.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAssignments = true, errorMessage = null) }
            when (val result = repository.teachingAssignments()) {
                is Resource.Success -> _uiState.update {
                    it.copy(assignments = result.data, isLoadingAssignments = false)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoadingAssignments = false, errorMessage = result.message)
                }
            }
        }
    }

    /**
     * Simpan tugas. `taskId` null = tugas baru pada `assignment`; selain itu ubah tugas tersebut
     * (kelas + mapel tidak bisa diganti).
     */
    fun save(
        taskId: Int?,
        assignment: TeachingAssignment?,
        title: String,
        description: String,
        dueDate: LocalDate?
    ) {
        if (_uiState.value.isSaving) return

        val state = _uiState.value.copy(
            errorMessage = null,
            assignmentError = if (taskId == null && assignment == null) R.string.task_error_assignment else null,
            titleError = titleError(title),
            descriptionError = if (description.trim().length > MAX_DESCRIPTION) R.string.task_error_description_long else null,
            dueDateError = dueDateError(dueDate, isNew = taskId == null)
        )
        val valid = state.assignmentError == null && state.titleError == null &&
            state.descriptionError == null && state.dueDateError == null
        if (!valid) {
            _uiState.value = state
            return
        }

        _uiState.value = state.copy(isSaving = true)
        val input = TaskInput(title, description, checkNotNull(dueDate))
        viewModelScope.launch {
            val result = if (taskId == null) {
                repository.create(checkNotNull(assignment).id, input)
            } else {
                repository.update(taskId, input)
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    savedChannel.send(Unit)
                }
                is Resource.Error -> _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
            }
        }
    }

    private fun titleError(title: String): Int? = when {
        title.isBlank() -> R.string.task_error_title_required
        title.trim().length > MAX_TITLE -> R.string.task_error_title_long
        else -> null
    }

    // Server menolak tenggat sebelum hari ini hanya saat membuat; mengubah tugas lama tetap boleh.
    private fun dueDateError(dueDate: LocalDate?, isNew: Boolean): Int? = when {
        dueDate == null -> R.string.task_error_due_required
        isNew && dueDate.isBefore(DateTimeWib.today()) -> R.string.task_error_due_past
        else -> null
    }

    private companion object {
        const val MAX_TITLE = 150
        const val MAX_DESCRIPTION = 2000
    }
}
