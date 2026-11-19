package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ProgressNoteDto(
    val id: Int,
    val studentId: Int,
    val teachingAssignmentId: Int,
    val date: String,
    val content: String,
    val studentName: String? = null,
    val nis: String? = null,
    val subjectName: String? = null,
    val className: String? = null
)

data class CreateProgressNoteRequest(
    val teachingAssignmentId: Int,
    val studentId: Int,
    val date: String,
    val content: String
)

data class UpdateProgressNoteRequest(val date: String, val content: String)

interface ProgressNoteApi {
    @GET("me/progress-notes")
    suspend fun mine(@Query("page") page: Int = 1, @Query("limit") limit: Int = 30): ApiResponse<List<ProgressNoteDto>>

    @GET("teaching-assignments/{assignmentId}/progress-notes")
    suspend fun assignment(
        @Path("assignmentId") assignmentId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30,
        @Query("studentId") studentId: Int? = null
    ): ApiResponse<List<ProgressNoteDto>>

    @POST("progress-notes")
    suspend fun create(@Body request: CreateProgressNoteRequest): ApiResponse<ProgressNoteDto>

    @PATCH("progress-notes/{noteId}")
    suspend fun update(@Path("noteId") noteId: Int, @Body request: UpdateProgressNoteRequest): ApiResponse<ProgressNoteDto>
}
