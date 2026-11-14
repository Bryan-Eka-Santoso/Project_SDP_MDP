package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

data class GradeScheduleDto(
    val id: Int,
    val day: String,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String
)

data class GradeAssignmentDto(
    val id: Int,
    val label: String,
    val classGroupId: Int,
    val subjectName: String? = null,
    val className: String? = null,
    val semesterName: String? = null,
    val schedules: List<GradeScheduleDto> = emptyList()
)

data class GradeStudentDto(val id: Int, val name: String, val nis: String?)
data class GradeComponentDto(val id: Int, val name: String, val weight: Int, val teachingAssignmentId: Int)
data class GradeScoreDto(val id: Int, val studentId: Int, val gradeComponentId: Int, val teachingAssignmentId: Int, val value: Double, val inputDate: String?)
data class GradeComponentRequest(val name: String, val weight: Int)
data class GradeScoreRequest(val teachingAssignmentId: Int, val gradeComponentId: Int, val studentId: Int, val value: Double)
data class GradePaginationDto(val page: Int, val limit: Int, val total: Int, val totalPages: Int)
data class GradePageResponse<T>(val success: Boolean, val data: List<T>, val meta: GradePaginationDto)

interface GradesApi {
    @GET("me/grade-teaching-assignments")
    suspend fun assignments(): ApiResponse<List<GradeAssignmentDto>>

    @GET("teaching-assignments/{assignmentId}/students")
    suspend fun students(@Path("assignmentId") assignmentId: Int): ApiResponse<List<GradeStudentDto>>

    @GET("teaching-assignments/{assignmentId}/grade-components")
    suspend fun components(@Path("assignmentId") assignmentId: Int): ApiResponse<List<GradeComponentDto>>

    @POST("teaching-assignments/{assignmentId}/grade-components")
    suspend fun createComponent(@Path("assignmentId") assignmentId: Int, @Body body: GradeComponentRequest): ApiResponse<GradeComponentDto>

    @PATCH("grade-components/{id}")
    suspend fun updateComponent(@Path("id") id: Int, @Body body: GradeComponentRequest): ApiResponse<GradeComponentDto>

    @GET("scores")
    suspend fun scores(@Query("teachingAssignmentId") assignmentId: Int, @Query("page") page: Int = 1, @Query("limit") limit: Int = 100): GradePageResponse<GradeScoreDto>

    @POST("scores")
    suspend fun saveScore(@Body body: GradeScoreRequest): ApiResponse<GradeScoreDto>
}
