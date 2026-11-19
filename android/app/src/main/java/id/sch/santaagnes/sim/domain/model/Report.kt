package id.sch.santaagnes.sim.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Satu halaman laporan berpaginasi; `total` = jumlah seluruh baris sesuai filter. */
data class ReportPage<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean,
    val total: Int
)

/** Satu baris laporan perubahan jadwal (F-LAP-07). */
data class ScheduleChangeReportRow(
    val id: Int,
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val classGroupName: String,
    val subjectName: String,
    val originalTeacherName: String,
    val type: ChangeType,
    val substituteTeacherName: String?,
    val originalRoomName: String?,
    val newRoomName: String?,
    val originalStart: LocalTime,
    val originalEnd: LocalTime,
    val newStart: LocalTime?,
    val newEnd: LocalTime?,
    val description: String,
    // Tanggal tujuan bila pertemuan dipindah ke hari lain.
    val newDate: LocalDate? = null
)

/** Filter laporan perubahan jadwal; null = tidak difilter. Semester selalu semester aktif. */
data class ScheduleChangeReportFilter(
    val classGroupId: Int? = null,
    // guru asli atau guru pengganti
    val teacherId: Int? = null,
    val type: ChangeType? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null
)

/** Satu baris laporan beban mengajar guru (F-LAP-09), dihitung dari jadwal master semester aktif. */
data class TeachingLoadRow(
    val teacherId: Int,
    val teacherName: String,
    val employeeNumber: String?,
    val sessionsPerWeek: Int,
    val totalHours: Double,
    val classCount: Int,
    val subjectNames: List<String>,
    // jam per hari, urut Senin sampai Sabtu
    val hoursPerDay: List<Double>
)

data class TeachingLoadFilter(
    val teacherId: Int? = null
)

/** Filter laporan status pembayaran (F-LAP-05); null = tidak difilter. */
data class PaymentStatusReportFilter(
    val classGroupId: Int? = null,
    val status: BillStatus? = null,
    val dueFrom: LocalDate? = null,
    val dueTo: LocalDate? = null
)

/** Satu baris laporan statistik kesiswaan per kelas (F-LAP-10) pada semester aktif. */
data class StudentStatsRow(
    val classGroupId: Int,
    val className: String,
    val gradeLevel: String,
    val capacity: Int,
    val homeroomTeacherName: String?,
    val studentCount: Int,
    val presentCount: Int,
    val sickCount: Int,
    val excusedCount: Int,
    val absentCount: Int,
    val attendanceTotal: Int,
    val attendanceRate: Double,
    val violationCount: Int,
    val violationPoints: Int
)

/** Filter laporan statistik kesiswaan (F-LAP-10). */
data class StudentStatsFilter(
    val classGroupId: Int? = null,
    val gradeLevel: String? = null
)

/** Pilihan filter laporan: semua guru, kelas tahun ajaran aktif, dan daftar tingkat. */
data class ReportOptions(
    val teachers: List<TeacherOption> = emptyList(),
    val classGroups: List<NamedId> = emptyList(),
    val gradeLevels: List<String> = emptyList()
)
