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
    val description: String,
    // Tanggal tujuan bila pertemuan dipindah ke hari lain.
    val newDate: String? = null
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

/** ABS-04: satu sesi jurnal mengajar yang dimonitor Admin. */
data class AdminJournalDto(
    val id: Int,
    val date: String,
    val journal: String?,
    val scheduleSlotId: Int,
    val teachingAssignmentId: Int,
    val teacherName: String,
    val subjectName: String,
    val className: String
)

/** LAP-01: per siswa dan mata pelajaran; nilai akhir null saat komponen belum lengkap. */
data class GradesAdminReportDto(
    val studentId: Int,
    val nis: String,
    val studentName: String,
    val teachingAssignmentId: Int,
    val className: String,
    val subjectName: String,
    val complete: Boolean,
    val finalScore: Double?,
    val predicate: String?
)

/** LAP-02: satu baris rekap berdasarkan sesi presensi yang tercatat. */
data class AttendanceAdminReportDto(
    val studentId: Int,
    val nis: String,
    val studentName: String,
    val classGroupId: Int,
    val className: String,
    val present: Int,
    val sick: Int,
    val excused: Int,
    val absent: Int,
    val total: Int,
    val attendanceRate: Double?
)

/** LAP-06: satu baris per sesi mengajar; berbeda dari monitor ABS-04. */
data class TeachingJournalReportDto(
    val id: Int,
    val date: String,
    val journal: String?,
    val classGroupId: Int,
    val className: String,
    val teacherId: Int,
    val teacherName: String,
    val subjectName: String
)

data class ViolationReportDto(
    val id: Int,
    val date: String,
    val studentId: Int,
    val nis: String,
    val studentName: String,
    val className: String,
    val violationTypeId: Int,
    val violationTypeName: String,
    val points: Int,
    val reporterName: String,
    val description: String?
)

data class FacilityBookingReportDto(
    val id: Int,
    val facilityId: Int,
    val facilityName: String,
    val facilityType: String,
    val requesterId: Int,
    val requesterName: String,
    val requesterEmail: String,
    val bookingDate: String,
    val startTime: String,
    val endTime: String,
    val purpose: String,
    val status: String,
    val verifierName: String?,
    val returnDate: String?,
    val rejectionReason: String? = null
)

data class ElectiveSurveyReportDto(
    val surveyId: Int,
    val surveyTitle: String,
    val academicYearName: String,
    val gradeLevel: String,
    val openDate: String,
    val closeDate: String,
    val status: String,
    val subjectId: Int,
    val subjectCode: String,
    val subjectName: String,
    val studentCount: Int,
    val respondentCount: Int,
    val percentage: Double
)

data class StudentStatsClassDto(
    val id: Int,
    val name: String,
    val gradeLevel: String,
    val capacity: Int
)

data class StudentStatsAttendanceDto(
    val present: Int,
    val sick: Int,
    val excused: Int,
    val absent: Int,
    val total: Int
)

data class StudentStatsViolationsDto(
    val count: Int,
    val points: Int
)

/** Satu baris `GET /reports/student-stats` (F-LAP-10). */
data class StudentStatsDto(
    val classGroup: StudentStatsClassDto,
    val homeroomTeacher: IdNameDto?,
    val studentCount: Int,
    val attendance: StudentStatsAttendanceDto,
    val attendanceRate: Double,
    val violations: StudentStatsViolationsDto
)

interface ReportApi {
    // LAP-06: setiap sesi jurnal pada semester aktif; filter sama dengan export XLSX.
    @GET("reports/teaching-journals")
    suspend fun getTeachingJournalsReport(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("teacherId") teacherId: Int? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ApiResponse<List<TeachingJournalReportDto>>

    @Streaming
    @GET("reports/teaching-journals/export")
    suspend fun exportTeachingJournalsReport(
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("teacherId") teacherId: Int? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ResponseBody
    // LAP-02: laporan kehadiran per siswa/kelas, semester aktif bila tanpa semesterId.
    @GET("reports/attendance")
    suspend fun getAttendanceReport(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ApiResponse<List<AttendanceAdminReportDto>>

    @Streaming
    @GET("reports/attendance/export")
    suspend fun exportAttendanceReport(
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ResponseBody


    @GET("reports/grades")
    suspend fun getGradesReport(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("classGroupId") classGroupId: Int? = null
    ): ApiResponse<List<GradesAdminReportDto>>

    @Streaming
    @GET("reports/grades/export")
    suspend fun exportGradesReport(
        @Query("classGroupId") classGroupId: Int? = null
    ): ResponseBody

    // ABS-04: monitoring jurnal mengajar oleh Admin.
    @GET("admin/teaching-journals")
    suspend fun getAdminJournals(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("teacherId") teacherId: Int? = null,
        @Query("classGroupId") classGroupId: Int? = null
    ): ApiResponse<List<AdminJournalDto>>


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

    @GET("reports/payment-status")
    suspend fun getPaymentStatus(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("classGroupId") classGroupId: Int?,
        @Query("status") status: String?,
        @Query("dueFrom") dueFrom: String?,
        @Query("dueTo") dueTo: String?
    ): ApiResponse<List<BillDto>>

    @GET("reports/student-stats")
    suspend fun getStudentStats(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("classGroupId") classGroupId: Int?,
        @Query("gradeLevel") gradeLevel: String?
    ): ApiResponse<List<StudentStatsDto>>

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

    @Streaming
    @GET("reports/payment-status/export")
    suspend fun exportPaymentStatus(
        @Query("classGroupId") classGroupId: Int?,
        @Query("status") status: String?,
        @Query("dueFrom") dueFrom: String?,
        @Query("dueTo") dueTo: String?
    ): ResponseBody

    @Streaming
    @GET("reports/student-stats/export")
    suspend fun exportStudentStats(
        @Query("classGroupId") classGroupId: Int?,
        @Query("gradeLevel") gradeLevel: String?
    ): ResponseBody

    @GET("reports/violations")
    suspend fun getViolations(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("violationTypeId") violationTypeId: Int? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ApiResponse<List<ViolationReportDto>>

    @Streaming
    @GET("reports/violations/export")
    suspend fun exportViolations(
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("violationTypeId") violationTypeId: Int? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ResponseBody

    @GET("reports/facility-bookings")
    suspend fun getFacilityBookings(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("facilityId") facilityId: Int? = null,
        @Query("status") status: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ApiResponse<List<FacilityBookingReportDto>>

    @Streaming
    @GET("reports/facility-bookings/export")
    suspend fun exportFacilityBookings(
        @Query("facilityId") facilityId: Int? = null,
        @Query("status") status: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): ResponseBody

    @GET("reports/elective-surveys")
    suspend fun getElectiveSurveys(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("surveyId") surveyId: Int? = null,
        @Query("academicYearId") academicYearId: Int? = null,
        @Query("gradeLevel") gradeLevel: String? = null,
        @Query("status") status: String? = null
    ): ApiResponse<List<ElectiveSurveyReportDto>>

    @Streaming
    @GET("reports/elective-surveys/export")
    suspend fun exportElectiveSurveys(
        @Query("surveyId") surveyId: Int? = null,
        @Query("academicYearId") academicYearId: Int? = null,
        @Query("gradeLevel") gradeLevel: String? = null,
        @Query("status") status: String? = null
    ): ResponseBody
}
