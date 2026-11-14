package id.sch.santaagnes.sim.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ChangeType { SUBSTITUTE, RESCHEDULE, CANCEL }

data class ScheduleChange(
    val type: ChangeType,
    val description: String?
)

/** Nilai semula sebelum perubahan; hanya ada bila pelajaran itu berubah. */
data class ScheduleOriginal(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val roomName: String?,
    val teacherName: String?
)

data class ScheduleEntry(
    val date: LocalDate,
    val slotId: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subjectId: Int,
    val subjectCode: String?,
    val subjectName: String,
    val teacherName: String,
    val roomName: String?,
    val classGroupName: String,
    val change: ScheduleChange?,
    val original: ScheduleOriginal?
) {
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek
}
