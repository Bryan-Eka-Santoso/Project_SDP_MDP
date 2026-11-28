package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class TaskTeachingAssignmentDto(
    val id: Int,
    val teacher: IdNameDto?,
    val subject: SubjectDto,
    val classGroup: IdNameDto
)

/** Tugas dari guru (F-JDW-08/09). */
data class TaskDto(
    val id: Int,
    val title: String,
    val description: String?,
    val dueDate: String,
    // "HH:mm" WIB; null pada tugas lama sebelum jam tenggat ada
    val dueTime: String?,
    val teachingAssignment: TaskTeachingAssignmentDto,
    val createdAt: String? = null
)

/** Penugasan mengajar milik guru yang masuk, untuk memilih kelas + mapel saat membuat tugas. */
data class MyTeachingAssignmentDto(
    val id: Int,
    val subject: SubjectDto,
    val classGroup: IdNameDto
)

// Gson tidak menulis field null, jadi catatan kosong dikirim sebagai "" (server menyimpannya sebagai null).
data class CreateTaskRequest(
    val teachingAssignmentId: Int,
    val title: String,
    val description: String,
    val dueDate: String,
    val dueTime: String
)

data class UpdateTaskRequest(
    val title: String,
    val description: String,
    val dueDate: String,
    val dueTime: String
)

interface TaskApi {

    @GET("tasks")
    suspend fun getTasks(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        // hanya tugas bertenggat tanggal ini ke depan (YYYY-MM-DD)
        @Query("dueFrom") dueFrom: String? = null,
        // wajib untuk wali murid (anak yang dilihat); peran lain tidak boleh mengirimnya
        @Query("studentId") studentId: Int? = null
    ): ApiResponse<List<TaskDto>>

    @POST("tasks")
    suspend fun createTask(@Body body: CreateTaskRequest): ApiResponse<TaskDto>

    @PATCH("tasks/{id}")
    suspend fun updateTask(@Path("id") id: Int, @Body body: UpdateTaskRequest): ApiResponse<TaskDto>

    @DELETE("tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Int): Response<Unit>

    @GET("me/teaching-assignments")
    suspend fun getMyTeachingAssignments(): ApiResponse<List<MyTeachingAssignmentDto>>
}
