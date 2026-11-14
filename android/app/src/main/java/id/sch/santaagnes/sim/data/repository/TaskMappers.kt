package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.MyTeachingAssignmentDto
import id.sch.santaagnes.sim.data.remote.TaskDto
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import java.time.LocalDate

fun TaskDto.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description?.takeIf { it.isNotBlank() },
    dueDate = LocalDate.parse(dueDate),
    subjectName = teachingAssignment.subject.name,
    classGroupName = teachingAssignment.classGroup.name,
    teacherName = teachingAssignment.teacher?.name
)

fun MyTeachingAssignmentDto.toDomain(): TeachingAssignment = TeachingAssignment(
    id = id,
    subjectName = subject.name,
    classGroupName = classGroup.name
)
