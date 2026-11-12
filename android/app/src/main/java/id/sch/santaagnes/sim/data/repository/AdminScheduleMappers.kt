package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.ChangeDto
import id.sch.santaagnes.sim.data.remote.LookupAssignmentDto
import id.sch.santaagnes.sim.data.remote.SlotDto
import id.sch.santaagnes.sim.domain.model.AdminChange
import id.sch.santaagnes.sim.domain.model.AdminSlot
import id.sch.santaagnes.sim.domain.model.AssignmentOption
import id.sch.santaagnes.sim.domain.model.ChangeType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

fun SlotDto.toDomain(): AdminSlot {
    val assignment = teachingAssignment
    return AdminSlot(
        id = id,
        dayOfWeek = DayOfWeek.valueOf(dayOfWeek),
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
        roomId = room?.id,
        roomName = room?.name,
        assignmentId = assignment.id,
        teacherId = assignment.teacher.id,
        teacherName = assignment.teacher.name,
        subjectName = assignment.subject.name,
        classGroupId = assignment.classGroup.id,
        classGroupName = assignment.classGroup.name
    )
}

fun ChangeDto.toDomain(): AdminChange = AdminChange(
    id = id,
    date = LocalDate.parse(date),
    type = ChangeType.valueOf(type),
    description = description,
    substituteTeacherId = substituteTeacher?.id,
    substituteTeacherName = substituteTeacher?.name,
    newRoomId = newRoom?.id,
    newRoomName = newRoom?.name,
    newStartTime = newStartTime?.let(LocalTime::parse),
    newEndTime = newEndTime?.let(LocalTime::parse),
    slot = slot.toDomain()
)

fun LookupAssignmentDto.toDomain(): AssignmentOption = AssignmentOption(
    id = id,
    classGroupId = classGroup.id,
    classGroupName = classGroup.name,
    subjectName = subject.name,
    teacherId = teacher.id,
    teacherName = teacher.user.name
)
