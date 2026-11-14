package id.sch.santaagnes.sim.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.TaskRepository
import id.sch.santaagnes.sim.data.repository.TodoRepository
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.domain.model.Todo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StudentTaskTab { TASKS, REMINDERS }

data class StudentTaskRow(val task: Task, val done: Boolean)

/** Tugas dari guru untuk satu mapel. */
data class SubjectTasks(val subjectName: String, val rows: List<StudentTaskRow>)

data class StudentTaskUiState(
    val tab: StudentTaskTab = StudentTaskTab.TASKS,
    val groups: List<SubjectTasks> = emptyList(),
    val todos: List<Todo> = emptyList(),
    // muat tugas dari server; pengingat langsung dari perangkat
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isTasksEmpty: Boolean get() = groups.isEmpty() && !isLoading && errorMessage == null
}

@HiltViewModel
class StudentTaskViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val local: TodoRepository
) : ViewModel() {

    private data class Remote(
        val tasks: List<Task> = emptyList(),
        val isLoading: Boolean = true,
        val errorMessage: String? = null
    )

    private val tab = MutableStateFlow(StudentTaskTab.TASKS)
    private val remote = MutableStateFlow(Remote())

    val uiState: StateFlow<StudentTaskUiState> = combine(
        tab, remote, local.observeDoneTaskIds(), local.observeTodos()
    ) { selected, remoteState, doneIds, todos ->
        StudentTaskUiState(
            tab = selected,
            groups = group(remoteState.tasks, doneIds),
            todos = todos,
            isLoading = remoteState.isLoading,
            errorMessage = remoteState.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentTaskUiState())

    init {
        reload()
    }

    fun selectTab(selected: StudentTaskTab) {
        tab.value = selected
    }

    fun reload() {
        viewModelScope.launch {
            remote.update { it.copy(isLoading = true, errorMessage = null) }
            val loaded = mutableListOf<Task>()
            var page = 1
            // Semua halaman semester aktif dimuat; batas halaman menjaga dari perulangan tak terbatas.
            while (page <= MAX_PAGES) {
                when (val result = tasks.tasks(page)) {
                    is Resource.Success -> {
                        loaded += result.data.tasks
                        if (!result.data.hasMore) break
                        page += 1
                    }
                    is Resource.Error -> {
                        remote.update { it.copy(isLoading = false, errorMessage = result.message) }
                        return@launch
                    }
                }
            }
            remote.value = Remote(tasks = loaded, isLoading = false)
        }
    }

    fun setTaskDone(taskId: Int, done: Boolean) {
        viewModelScope.launch { local.setTaskDone(taskId, done) }
    }

    fun setTodoCompleted(todoId: Long, completed: Boolean) {
        viewModelScope.launch { local.setCompleted(todoId, completed) }
    }

    fun deleteTodo(todoId: Long) {
        viewModelScope.launch { local.delete(todoId) }
    }

    // Per mapel (urut abjad); dalam mapel yang belum selesai lebih dulu, lalu tenggat terdekat.
    private fun group(all: List<Task>, doneIds: Set<Int>): List<SubjectTasks> =
        all.groupBy { it.subjectName }
            .toSortedMap()
            .map { (subject, items) ->
                SubjectTasks(
                    subjectName = subject,
                    rows = items.map { StudentTaskRow(it, it.id in doneIds) }
                        .sortedWith(compareBy({ it.done }, { it.task.dueDate }, { it.task.id }))
                )
            }

    private companion object {
        const val MAX_PAGES = 10
    }
}
