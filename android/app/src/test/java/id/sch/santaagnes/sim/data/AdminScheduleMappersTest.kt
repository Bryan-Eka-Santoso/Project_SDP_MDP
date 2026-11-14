package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.remote.ChangeDto
import id.sch.santaagnes.sim.data.remote.IdNameDto
import id.sch.santaagnes.sim.data.remote.LookupAssignmentDto
import id.sch.santaagnes.sim.data.remote.LookupTeacherDto
import id.sch.santaagnes.sim.data.remote.LookupTeacherUserDto
import id.sch.santaagnes.sim.data.remote.SlotAssignmentDto
import id.sch.santaagnes.sim.data.remote.SlotDto
import id.sch.santaagnes.sim.data.remote.SubjectDto
import id.sch.santaagnes.sim.data.repository.toDomain
import id.sch.santaagnes.sim.domain.model.ChangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class AdminScheduleMappersTest {

    private val slotDto = SlotDto(
        id = 10,
        semesterId = 1,
        dayOfWeek = "WEDNESDAY",
        startTime = "07:00",
        endTime = "08:30",
        room = IdNameDto(2, "R-102"),
        teachingAssignment = SlotAssignmentDto(
            id = 7,
            teacher = IdNameDto(3, "Guru Satu"),
            subject = SubjectDto(4, "MTK", "Matematika"),
            classGroup = IdNameDto(5, "X-1")
        )
    )

    @Test
    fun mapsSlot() {
        val slot = slotDto.toDomain()

        assertEquals(DayOfWeek.WEDNESDAY, slot.dayOfWeek)
        assertEquals(LocalTime.of(7, 0), slot.startTime)
        assertEquals("R-102", slot.roomName)
        assertEquals(7, slot.assignmentId)
        assertEquals("Guru Satu", slot.teacherName)
        assertEquals("Matematika", slot.subjectName)
        assertEquals("X-1", slot.classGroupName)
    }

    @Test
    fun slotWithoutRoomHasNullRoom() {
        val slot = slotDto.copy(room = null).toDomain()

        assertNull(slot.roomId)
        assertNull(slot.roomName)
    }

    @Test
    fun mapsSubstituteChange() {
        val change = ChangeDto(
            id = 1,
            scheduleSlotId = 10,
            date = "2026-11-18",
            type = "SUBSTITUTE",
            substituteTeacher = IdNameDto(4, "Guru Dua"),
            newRoom = null,
            newStartTime = null,
            newEndTime = null,
            description = "Berhalangan",
            slot = slotDto
        ).toDomain()

        assertEquals(LocalDate.of(2026, 11, 18), change.date)
        assertEquals(ChangeType.SUBSTITUTE, change.type)
        assertEquals(4, change.substituteTeacherId)
        assertEquals("Guru Dua", change.substituteTeacherName)
        assertNull(change.newStartTime)
    }

    @Test
    fun mapsRescheduleChange() {
        val change = ChangeDto(
            id = 2,
            scheduleSlotId = 10,
            date = "2026-11-18",
            type = "RESCHEDULE",
            substituteTeacher = null,
            newRoom = IdNameDto(9, "Lab"),
            newStartTime = "09:00",
            newEndTime = "10:30",
            description = "Pindah ke lab",
            slot = slotDto
        ).toDomain()

        assertEquals(9, change.newRoomId)
        assertEquals(LocalTime.of(9, 0), change.newStartTime)
        assertEquals(LocalTime.of(10, 30), change.newEndTime)
    }

    @Test
    fun mapsLookupAssignment() {
        val option = LookupAssignmentDto(
            id = 7,
            teacher = LookupTeacherDto(3, LookupTeacherUserDto("Guru Satu")),
            subject = SubjectDto(4, null, "Matematika"),
            classGroup = IdNameDto(5, "X-1")
        ).toDomain()

        assertEquals(7, option.id)
        assertEquals(3, option.teacherId)
        assertEquals("Guru Satu", option.teacherName)
        assertEquals("X-1", option.classGroupName)
    }
}
