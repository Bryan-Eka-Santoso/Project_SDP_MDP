package id.sch.santaagnes.sim.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ChangeType { SUBSTITUTE, RESCHEDULE, CANCEL }

data class ScheduleChange(
    val type: ChangeType,
    val description: String?,
    // id perubahan di server; 0 bila tidak diketahui (cache lama)
    val id: Int = 0,
    // Pindah lintas hari: pertemuan tanggal ini dipindah ke `movedTo`, atau pertemuan ini pindahan dari `movedFrom`.
    val movedTo: LocalDate? = null,
    val movedFrom: LocalDate? = null
)

/** Data form guru untuk memindah satu pertemuan; yang null berarti tidak diubah. */
data class RescheduleInput(
    val slotId: Int,
    // tanggal asli pertemuan menurut jadwal mingguan
    val date: LocalDate,
    val newDate: LocalDate?,
    val newStartTime: LocalTime?,
    val newEndTime: LocalTime?,
    val newRoomId: Int?,
    val description: String
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

    /** Tanggal asli pertemuan ini menurut jadwal mingguan (kunci perubahan di server). */
    val occurrenceDate: LocalDate get() = change?.movedFrom ?: date

    /** Pertemuan pada tanggal ini tidak berlangsung: dibatalkan atau dipindah ke tanggal lain. */
    val isAway: Boolean get() = change?.type == ChangeType.CANCEL || change?.movedTo != null
}
