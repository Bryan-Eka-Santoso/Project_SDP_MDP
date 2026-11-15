package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.remote.IdNameDto
import id.sch.santaagnes.sim.data.remote.MyTeachingAssignmentDto
import id.sch.santaagnes.sim.data.remote.SubjectDto
import id.sch.santaagnes.sim.data.remote.TaskDto
import id.sch.santaagnes.sim.data.remote.TaskTeachingAssignmentDto
import id.sch.santaagnes.sim.data.repository.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TaskMappersTest {

    private fun dto(description: String?, dueTime: String? = "14:30") = TaskDto(
        id = 3,
        title = "Latihan Bab 3",
        description = description,
        dueDate = "2026-11-20",
        dueTime = dueTime,
        teachingAssignment = TaskTeachingAssignmentDto(
            id = 7,
            teacher = IdNameDto(1, "Guru Satu"),
            subject = SubjectDto(2, "MAT", "Matematika"),
            classGroup = IdNameDto(5, "X-1")
        )
    )

    @Test
    fun mapsTaskFields() {
        val task = dto("Soal 1-10").toDomain()

        assertEquals(3, task.id)
        assertEquals(LocalDate.of(2026, 11, 20), task.dueDate)
        assertEquals(LocalTime.of(14, 30), task.dueTime)
        assertEquals("Matematika", task.subjectName)
        assertEquals("X-1", task.classGroupName)
        assertEquals("Guru Satu", task.teacherName)
        assertEquals("Soal 1-10", task.description)
    }

    @Test
    fun oldTaskWithoutTimeKeepsNullTime() {
        assertNull(dto("x", dueTime = null).toDomain().dueTime)
    }

    @Test
    fun blankNotesBecomeNull() {
        assertNull(dto(null).toDomain().description)
        assertNull(dto("   ").toDomain().description)
    }

    @Test
    fun mapsTeachingAssignment() {
        val assignment = MyTeachingAssignmentDto(7, SubjectDto(2, null, "Matematika"), IdNameDto(5, "X-1")).toDomain()

        assertEquals(7, assignment.id)
        assertEquals("Matematika", assignment.subjectName)
        assertEquals("X-1", assignment.classGroupName)
    }
}
