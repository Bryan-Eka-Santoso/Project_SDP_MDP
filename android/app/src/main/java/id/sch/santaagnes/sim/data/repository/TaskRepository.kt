package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateTaskRequest
import id.sch.santaagnes.sim.data.remote.TaskApi
import id.sch.santaagnes.sim.data.remote.UpdateTaskRequest
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.domain.model.TaskInput
import id.sch.santaagnes.sim.domain.model.TaskPage
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Tugas dari guru. Hanya online (tanpa cache Room); pelaku guru, siswa hanya membaca. */
interface TaskRepository {

    suspend fun tasks(page: Int): Resource<TaskPage>

    suspend fun teachingAssignments(): Resource<List<TeachingAssignment>>

    suspend fun create(teachingAssignmentId: Int, input: TaskInput): Resource<Task>

    suspend fun update(taskId: Int, input: TaskInput): Resource<Task>

    suspend fun delete(taskId: Int): Resource<Unit>
}

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val api: TaskApi,
    private val errors: ApiErrorMapper
) : TaskRepository {

    override suspend fun tasks(page: Int): Resource<TaskPage> = errors.call {
        val response = api.getTasks(page, PAGE_SIZE)
        check(response.success) { "Tugas gagal dimuat." }
        val totalPages = response.meta?.totalPages ?: page
        TaskPage(
            tasks = response.data.orEmpty().map { it.toDomain() },
            page = page,
            hasMore = page < totalPages
        )
    }

    override suspend fun teachingAssignments(): Resource<List<TeachingAssignment>> = errors.call {
        val response = api.getMyTeachingAssignments()
        check(response.success) { "Daftar kelas gagal dimuat." }
        response.data.orEmpty().map { it.toDomain() }
    }

    override suspend fun create(teachingAssignmentId: Int, input: TaskInput): Resource<Task> = errors.call {
        val response = api.createTask(
            CreateTaskRequest(
                teachingAssignmentId = teachingAssignmentId,
                title = input.title.trim(),
                description = input.description.trim(),
                dueDate = input.dueDate.toString()
            )
        )
        checkNotNull(response.data) { "Tugas gagal disimpan." }.toDomain()
    }

    override suspend fun update(taskId: Int, input: TaskInput): Resource<Task> = errors.call {
        val response = api.updateTask(
            taskId,
            UpdateTaskRequest(
                title = input.title.trim(),
                description = input.description.trim(),
                dueDate = input.dueDate.toString()
            )
        )
        checkNotNull(response.data) { "Tugas gagal disimpan." }.toDomain()
    }

    override suspend fun delete(taskId: Int): Resource<Unit> = errors.call {
        val response = api.deleteTask(taskId)
        // 404 = sudah terhapus (mis. dari perangkat lain); hasilnya sama dengan yang diminta.
        if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
