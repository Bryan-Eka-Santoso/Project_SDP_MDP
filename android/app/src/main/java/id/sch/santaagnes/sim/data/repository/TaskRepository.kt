package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateTaskRequest
import id.sch.santaagnes.sim.data.remote.TaskApi
import id.sch.santaagnes.sim.data.remote.UpdateTaskRequest
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.domain.model.TaskInput
import id.sch.santaagnes.sim.domain.model.TaskPage
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Tugas dari guru. Hanya online (tanpa cache Room); pelaku guru, siswa hanya membaca. */
interface TaskRepository {

    suspend fun tasks(page: Int): Resource<TaskPage>

    /**
     * Pengingat di beranda: [limit] tugas dengan tenggat terdekat (hari ini ke depan) yang belum ditandai
     * selesai. [studentId] hanya diisi wali murid (anak yang dilihat); siswa memakai null.
     */
    suspend fun reminders(studentId: Int?, limit: Int): Resource<List<Task>>

    suspend fun teachingAssignments(): Resource<List<TeachingAssignment>>

    suspend fun create(teachingAssignmentId: Int, input: TaskInput): Resource<Task>

    suspend fun update(taskId: Int, input: TaskInput): Resource<Task>

    suspend fun delete(taskId: Int): Resource<Unit>
}

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val api: TaskApi,
    private val errors: ApiErrorMapper,
    private val todos: TodoRepository
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

    override suspend fun reminders(studentId: Int?, limit: Int): Resource<List<Task>> = errors.call {
        val response = api.getTasks(
            page = 1,
            limit = REMINDER_FETCH,
            dueFrom = DateTimeWib.today().toString(),
            studentId = studentId
        )
        check(response.success) { "Tugas gagal dimuat." }
        // Tanda selesai tersimpan di perangkat siswa; wali murid tidak punya tanda itu.
        val done = if (studentId == null) todos.observeDoneTaskIds().first() else emptySet()
        pickReminders(response.data.orEmpty().map { it.toDomain() }, done, limit)
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
                dueDate = input.dueDate.toString(),
                dueTime = input.dueTime.toString()
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
                dueDate = input.dueDate.toString(),
                dueTime = input.dueTime.toString()
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
        // tugas yang diambil sebelum yang sudah selesai dibuang dan diambil yang terdekat
        const val REMINDER_FETCH = 50
    }
}
