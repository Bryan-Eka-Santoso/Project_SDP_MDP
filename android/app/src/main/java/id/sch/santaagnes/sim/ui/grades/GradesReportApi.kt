package id.sch.santaagnes.sim.ui.grades

import id.sch.santaagnes.sim.data.remote.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class ReportComponentDto(val id: Int, val name: String, val weight: Int, val value: Double?)
data class SubjectReportDto(
    val teachingAssignmentId: Int,
    val subjectName: String,
    val className: String,
    val complete: Boolean,
    val totalWeight: Int,
    val finalScore: Double?,
    val predicate: String?,
    val components: List<ReportComponentDto>
)
data class ClassStudentReportDto(
    val studentId: Int,
    val name: String,
    val nis: String?,
    val complete: Boolean,
    val totalWeight: Int,
    val finalScore: Double?,
    val predicate: String?,
    val components: List<ReportComponentDto>
)
data class ReportPageDto(val page: Int, val limit: Int, val total: Int, val totalPages: Int)
data class ClassReportResponse(val success: Boolean, val data: List<ClassStudentReportDto>, val meta: ReportPageDto)

interface GradesReportApi {
    @GET("me/scores")
    suspend fun ownScores(): ApiResponse<List<SubjectReportDto>>

    @GET("students/{studentId}/scores")
    suspend fun studentScores(@Path("studentId") studentId: Int): ApiResponse<List<SubjectReportDto>>

    @GET("teaching-assignments/{assignmentId}/grade-summary")
    suspend fun classReport(
        @Path("assignmentId") assignmentId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): ClassReportResponse
}
