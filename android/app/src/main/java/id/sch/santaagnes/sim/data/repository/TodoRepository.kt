package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.local.TaskDoneEntity
import id.sch.santaagnes.sim.data.local.TodoItem
import id.sch.santaagnes.sim.domain.model.SubjectOption
import id.sch.santaagnes.sim.domain.model.Todo
import id.sch.santaagnes.sim.domain.model.TodoInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Data siswa yang hanya ada di perangkat: pengingat tugas (F-JDW-07) dan tanda selesai tugas guru (F-JDW-09). */
interface TodoRepository {

    fun observeTodos(): Flow<List<Todo>>

    suspend fun add(input: TodoInput)

    suspend fun setCompleted(todoId: Long, completed: Boolean)

    suspend fun delete(todoId: Long)

    fun observeDoneTaskIds(): Flow<Set<Int>>

    suspend fun setTaskDone(taskId: Int, done: Boolean)

    /** Mapel yang dikenal dari jadwal siswa yang sudah tersimpan; kosong bila belum pernah memuat jadwal. */
    fun observeSubjects(): Flow<List<SubjectOption>>
}

@Singleton
class TodoRepositoryImpl @Inject constructor(
    private val dao: LocalDao,
    private val session: SessionManager
) : TodoRepository {

    override fun observeTodos(): Flow<List<Todo>> = flow {
        val accountId = session.getUserId()
        if (accountId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(dao.getTodos(accountId).map { rows -> rows.map { it.toDomain() } })
    }

    override suspend fun add(input: TodoInput) {
        val accountId = session.getUserId() ?: return
        dao.addTodo(
            TodoItem(
                accountId = accountId,
                title = input.title.trim(),
                notes = input.notes.trim().ifEmpty { null },
                dueDate = input.dueDate.toString(),
                subjectId = input.subject?.id,
                subjectName = input.subject?.name
            )
        )
    }

    override suspend fun setCompleted(todoId: Long, completed: Boolean) {
        val accountId = session.getUserId() ?: return
        dao.updateTodo(todoId, accountId, completed)
    }

    override suspend fun delete(todoId: Long) {
        val accountId = session.getUserId() ?: return
        dao.deleteTodo(todoId, accountId)
    }

    override fun observeDoneTaskIds(): Flow<Set<Int>> = flow {
        val accountId = session.getUserId()
        if (accountId == null) {
            emit(emptySet())
            return@flow
        }
        emitAll(dao.observeDoneTaskIds(accountId).map { it.toSet() })
    }

    override suspend fun setTaskDone(taskId: Int, done: Boolean) {
        val accountId = session.getUserId() ?: return
        if (done) dao.markTaskDone(TaskDoneEntity(accountId, taskId)) else dao.unmarkTaskDone(accountId, taskId)
    }

    override fun observeSubjects(): Flow<List<SubjectOption>> = flow {
        val accountId = session.getUserId()
        val studentId = session.getStudentId()
        if (accountId == null || studentId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(dao.observeSubjects(accountId, studentId).map { rows -> rows.map { SubjectOption(it.subjectId, it.subjectName) } })
    }

    private fun TodoItem.toDomain() = Todo(
        id = id,
        title = title,
        notes = notes,
        dueDate = LocalDate.parse(dueDate),
        subjectName = subjectName,
        isCompleted = isCompleted
    )
}
