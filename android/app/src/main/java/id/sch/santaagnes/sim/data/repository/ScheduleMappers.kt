package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.local.ScheduleEntity
import id.sch.santaagnes.sim.data.remote.ScheduleEntryDto
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ScheduleChange
import id.sch.santaagnes.sim.domain.model.ScheduleEntry
import id.sch.santaagnes.sim.domain.model.ScheduleOriginal
import java.time.LocalDate
import java.time.LocalTime

private fun parseChangeType(value: String?): ChangeType? =
    value?.let { runCatching { ChangeType.valueOf(it) }.getOrNull() }

fun ScheduleEntryDto.toDomain(): ScheduleEntry {
    // Jenis perubahan yang tidak dikenal (backend lebih baru) diabaikan, pelajarannya tetap tampil.
    val type = parseChangeType(change?.type)
    return ScheduleEntry(
        date = LocalDate.parse(date),
        slotId = slotId,
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
        subjectId = subject.id,
        subjectCode = subject.code,
        subjectName = subject.name,
        teacherName = teacher.name,
        roomName = room?.name,
        classGroupName = classGroup.name,
        change = type?.let { ScheduleChange(it, change?.description) },
        original = if (type != null) {
            original?.let {
                ScheduleOriginal(
                    startTime = LocalTime.parse(it.startTime),
                    endTime = LocalTime.parse(it.endTime),
                    roomName = it.room?.name,
                    teacherName = it.teacher?.name
                )
            }
        } else {
            null
        }
    )
}

fun ScheduleEntry.toEntity(accountId: Int, scope: Int): ScheduleEntity = ScheduleEntity(
    accountId = accountId,
    scope = scope,
    date = date.toString(),
    slotId = slotId,
    startTime = startTime.toString(),
    endTime = endTime.toString(),
    subjectId = subjectId,
    subjectCode = subjectCode,
    subjectName = subjectName,
    teacherName = teacherName,
    roomName = roomName,
    classGroupName = classGroupName,
    changeType = change?.type?.name,
    changeDescription = change?.description,
    originalStartTime = original?.startTime?.toString(),
    originalEndTime = original?.endTime?.toString(),
    originalRoomName = original?.roomName,
    originalTeacherName = original?.teacherName
)

fun ScheduleEntity.toDomain(): ScheduleEntry {
    val type = parseChangeType(changeType)
    return ScheduleEntry(
        date = LocalDate.parse(date),
        slotId = slotId,
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
        subjectId = subjectId,
    subjectCode = subjectCode,
        subjectName = subjectName,
        teacherName = teacherName,
        roomName = roomName,
        classGroupName = classGroupName,
        change = type?.let { ScheduleChange(it, changeDescription) },
        original = if (type != null && originalStartTime != null && originalEndTime != null) {
            ScheduleOriginal(
                startTime = LocalTime.parse(originalStartTime),
                endTime = LocalTime.parse(originalEndTime),
                roomName = originalRoomName,
                teacherName = originalTeacherName
            )
        } else {
            null
        }
    )
}
