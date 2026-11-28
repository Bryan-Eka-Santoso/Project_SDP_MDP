package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AttendanceSlotDto(
    val id: Int,
    val dayOfWeek: String,
    val assignmentId: Int,
    val subjectName: String,
    val className: String,
    val isSubstitute: Boolean = false,
    val substituteDates: List<String>? = null
)

data class AttendanceStudentDto(val id: Int, val name: String, val nis: String?)
data class AttendanceEntryDto(val id: Int, val name: String, val nis: String?, val status: String?)
data class AttendanceSessionDto(
    val slotId: Int,
    val date: String,
    val journal: String,
    val saved: Boolean,
    val students: List<AttendanceEntryDto>
)
data class AttendanceInputDto(val studentId: Int, val status: String)
data class SaveAttendanceRequest(
    val date: String,
    val journal: String,
    val attendances: List<AttendanceInputDto>
)
data class SavedAttendanceDto(val id: Int, val slotId: Int, val count: Int, val saved: Boolean)

data class ClassAttendanceStudentDto(
    val studentId: Int,
    val nis: String?,
    val name: String,
    val counts: AttendanceCountsDto
)
data class ClassAttendanceSummaryDto(
    val teachingAssignmentId: Int,
    val students: List<ClassAttendanceStudentDto>
)

interface AttendanceApi {
    @GET("teaching-assignments/{assignmentId}/attendance-summary")
    suspend fun classAttendanceSummary(
        @Path("assignmentId") assignmentId: Int,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): ApiResponse<ClassAttendanceSummaryDto>

    @GET("me/attendance-schedule-slots")
    suspend fun slots(): ApiResponse<List<AttendanceSlotDto>>

    @GET("schedule-slots/{slotId}/students")
    suspend fun students(@Path("slotId") slotId: Int): ApiResponse<List<AttendanceStudentDto>>

    @GET("schedule-slots/{slotId}/attendance")
    suspend fun session(@Path("slotId") slotId: Int, @Query("date") date: String): ApiResponse<AttendanceSessionDto>

    @POST("schedule-slots/{slotId}/attendance")
    suspend fun save(@Path("slotId") slotId: Int, @Body request: SaveAttendanceRequest): ApiResponse<SavedAttendanceDto>

    // ABS-03: Rekap presensi siswa yang sedang login
    @GET("me/attendance-summary")
    suspend fun myAttendanceSummary(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): ApiResponse<AttendanceSummaryDto>

    // ABS-03: Rekap presensi anak yang terhubung ke wali murid
    @GET("students/{studentId}/attendance-summary")
    suspend fun studentAttendanceSummary(
        @Path("studentId") studentId: Int,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): ApiResponse<AttendanceSummaryDto>
}
