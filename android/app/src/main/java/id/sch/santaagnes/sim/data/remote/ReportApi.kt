package id.sch.santaagnes.sim.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Satu baris `GET /reports/schedule-changes` (F-LAP-07). Jam `HH:mm`, tanggal `YYYY-MM-DD`. */
data class ScheduleChangeReportDto(
    val id: Int,
    val date: String,
    val dayOfWeek: String,
    val classGroup: IdNameDto,
    val subject: SubjectDto,
    val originalTeacher: IdNameDto,
    val type: String,
    val substituteTeacher: IdNameDto?,
    val originalRoom: IdNameDto?,
    val originalStartTime: String,
    val originalEndTime: String,
    val newRoom: IdNameDto?,
    val newStartTime: String?,
    val newEndTime: String?,
    val description: String
)

data class ReportTeacherDto(
    val id: Int,
    val name: String,
    val employeeNumber: String?
)

data class ReportDayLoadDto(
    val sessions: Int,
    val minutes: Double
)

/** Satu baris `GET /reports/teaching-load` (F-LAP-09); `perDay` berkunci `MONDAY`..`SATURDAY`. */
data class TeachingLoadDto(
    val teacher: ReportTeacherDto,
    val sessionsPerWeek: Int,
    val totalMinutes: Double,
    val totalHours: Double,
    val classCount: Int,
    val subjects: List<SubjectDto>,
    val perDay: Map<String, ReportDayLoadDto>
)

interface ReportApi {

    // Tanpa semesterId server memakai semester aktif.
    @GET("reports/schedule-changes")
    suspend fun getScheduleChanges(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("classGroupId") classGroupId: Int?,
        @Query("teacherId") teacherId: Int?,
        @Query("type") type: String?,
        @Query("dateFrom") dateFrom: String?,
        @Query("dateTo") dateTo: String?
    ): ApiResponse<List<ScheduleChangeReportDto>>

    @GET("reports/teaching-load")
    suspend fun getTeachingLoad(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("teacherId") teacherId: Int?
    ): ApiResponse<List<TeachingLoadDto>>

    // F-LAP-11: berkas .xlsx berisi data dan filter yang sama dengan tampilan (tanpa paginasi).
    @Streaming
    @GET("reports/schedule-changes/export")
    suspend fun exportScheduleChanges(
        @Query("classGroupId") classGroupId: Int?,
        @Query("teacherId") teacherId: Int?,
        @Query("type") type: String?,
        @Query("dateFrom") dateFrom: String?,
        @Query("dateTo") dateTo: String?
    ): ResponseBody

    @Streaming
    @GET("reports/teaching-load/export")
    suspend fun exportTeachingLoad(
        @Query("teacherId") teacherId: Int?
    ): ResponseBody
}
