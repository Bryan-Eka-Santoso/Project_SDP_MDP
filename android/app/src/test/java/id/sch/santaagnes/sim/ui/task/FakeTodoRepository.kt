package id.sch.santaagnes.sim.ui.task

import id.sch.santaagnes.sim.data.repository.TodoRepository
import id.sch.santaagnes.sim.domain.model.SubjectOption
import id.sch.santaagnes.sim.domain.model.Todo
import id.sch.santaagnes.sim.domain.model.TodoInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTodoRepository : TodoRepository {
    val todos = MutableStateFlow<List<Todo>>(emptyList())
    val doneTaskIds = MutableStateFlow<Set<Int>>(emptySet())
    val subjects = MutableStateFlow<List<SubjectOption>>(emptyList())
    val added = mutableListOf<TodoInput>()
    private var nextId = 1L

    override fun observeTodos(): Flow<List<Todo>> = todos

    override suspend fun add(input: TodoInput) {
        added += input
        todos.value = todos.value + Todo(nextId++, input.title.trim(), input.notes.ifBlank { null }, input.dueDate, input.subject?.name, false)
    }

    override suspend fun setCompleted(todoId: Long, completed: Boolean) {
        todos.value = todos.value.map { if (it.id == todoId) it.copy(isCompleted = completed) else it }
    }

    override suspend fun delete(todoId: Long) {
        todos.value = todos.value.filterNot { it.id == todoId }
    }

    override fun observeDoneTaskIds(): Flow<Set<Int>> = doneTaskIds

    override suspend fun setTaskDone(taskId: Int, done: Boolean) {
        doneTaskIds.value = if (done) doneTaskIds.value + taskId else doneTaskIds.value - taskId
    }

    override fun observeSubjects(): Flow<List<SubjectOption>> = subjects
}
