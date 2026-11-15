package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.MyTeachingAssignmentDto
import id.sch.santaagnes.sim.data.remote.TaskDto
import id.sch.santaagnes.sim.core.DEFAULT_DUE_TIME
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import java.time.LocalDate
import java.time.LocalTime

fun TaskDto.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description?.takeIf { it.isNotBlank() },
    dueDate = LocalDate.parse(dueDate),
    dueTime = dueTime?.let(LocalTime::parse),
    subjectName = teachingAssignment.subject.name,
    classGroupName = teachingAssignment.classGroup.name,
    teacherName = teachingAssignment.teacher?.name
)

fun MyTeachingAssignmentDto.toDomain(): TeachingAssignment = TeachingAssignment(
    id = id,
    subjectName = subject.name,
    classGroupName = classGroup.name
)

/**
 * Tugas untuk pengingat beranda: buang yang sudah ditandai selesai, urutkan dari tenggat terdekat
 * (tanggal lalu jam; tanpa jam dianggap 23:59), ambil [limit] pertama.
 */
fun pickReminders(tasks: List<Task>, doneIds: Set<Int>, limit: Int): List<Task> =
    tasks
        .filterNot { it.id in doneIds }
        .sortedWith(compareBy({ it.dueDate }, { it.dueTime ?: DEFAULT_DUE_TIME }, { it.id }))
        .take(limit)
