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
    val description: String
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

/** Pilihan filter laporan: semua guru dan kelas yang punya penugasan di semester aktif. */
data class ReportOptions(
    val teachers: List<TeacherOption> = emptyList(),
    val classGroups: List<NamedId> = emptyList()
)
