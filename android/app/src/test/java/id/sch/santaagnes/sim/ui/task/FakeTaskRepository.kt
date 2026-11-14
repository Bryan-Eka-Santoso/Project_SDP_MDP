package id.sch.santaagnes.sim.ui.task

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.TaskRepository
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.domain.model.TaskInput
import id.sch.santaagnes.sim.domain.model.TaskPage
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import java.time.LocalDate

class FakeTaskRepository : TaskRepository {
    var pages: List<List<Task>> = listOf(emptyList())
    var tasksError: String? = null
    var assignmentsResult: Resource<List<TeachingAssignment>> = Resource.Success(emptyList())
    var saveError: String? = null
    var deleteError: String? = null

    val requestedPages = mutableListOf<Int>()
    val created = mutableListOf<Pair<Int, TaskInput>>()
    val updated = mutableListOf<Pair<Int, TaskInput>>()
    val deleted = mutableListOf<Int>()

    override suspend fun tasks(page: Int): Resource<TaskPage> {
        requestedPages += page
        tasksError?.let { return Resource.Error(it) }
        val items = pages.getOrElse(page - 1) { emptyList() }
        return Resource.Success(TaskPage(items, page, hasMore = page < pages.size))
    }

    override suspend fun teachingAssignments() = assignmentsResult

    override suspend fun create(teachingAssignmentId: Int, input: TaskInput): Resource<Task> {
        saveError?.let { return Resource.Error(it) }
        created += teachingAssignmentId to input
        return Resource.Success(task(99))
    }

    override suspend fun update(taskId: Int, input: TaskInput): Resource<Task> {
        saveError?.let { return Resource.Error(it) }
        updated += taskId to input
        return Resource.Success(task(taskId))
    }

    override suspend fun delete(taskId: Int): Resource<Unit> {
        deleteError?.let { return Resource.Error(it) }
        deleted += taskId
        return Resource.Success(Unit)
    }

    companion object {
        fun task(id: Int) = Task(
            id = id,
            title = "Tugas $id",
            description = null,
            dueDate = LocalDate.of(2026, 11, 20),
            subjectName = "Matematika",
            classGroupName = "X-1",
            teacherName = "Guru Satu"
        )
    }
}
