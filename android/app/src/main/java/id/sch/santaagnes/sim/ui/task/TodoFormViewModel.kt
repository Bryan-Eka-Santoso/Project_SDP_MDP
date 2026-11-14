package id.sch.santaagnes.sim.ui.task

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.repository.TodoRepository
import id.sch.santaagnes.sim.domain.model.SubjectOption
import id.sch.santaagnes.sim.domain.model.TodoInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodoFormUiState(
    val subjects: List<SubjectOption> = emptyList(),
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val notesError: Int? = null,
    @param:StringRes val dueDateError: Int? = null
)

@HiltViewModel
class TodoFormViewModel @Inject constructor(
    private val repository: TodoRepository
) : ViewModel() {

    private val errors = MutableStateFlow(TodoFormUiState())

    val uiState: StateFlow<TodoFormUiState> = combine(errors, repository.observeSubjects()) { state, subjects ->
        state.copy(subjects = subjects)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoFormUiState())

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Dipancarkan sekali setelah pengingat tersimpan. */
    val saved = savedChannel.receiveAsFlow()

    fun save(title: String, notes: String, dueDate: LocalDate?, subject: SubjectOption?) {
        val state = TodoFormUiState(
            titleError = when {
                title.isBlank() -> R.string.todo_error_title_required
                title.trim().length > MAX_TITLE -> R.string.task_error_title_long
                else -> null
            },
            notesError = if (notes.trim().length > MAX_NOTES) R.string.task_error_description_long else null,
            dueDateError = when {
                dueDate == null -> R.string.task_error_due_required
                dueDate.isBefore(DateTimeWib.today()) -> R.string.task_error_due_past
                else -> null
            }
        )
        errors.value = state
        if (state.titleError != null || state.notesError != null || state.dueDateError != null) return

        viewModelScope.launch {
            repository.add(TodoInput(title, notes, checkNotNull(dueDate), subject))
            savedChannel.send(Unit)
        }
    }

    private companion object {
        const val MAX_TITLE = 150
        const val MAX_NOTES = 2000
    }
}
