package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.remote.IdNameDto
import id.sch.santaagnes.sim.data.remote.ScheduleChangeDto
import id.sch.santaagnes.sim.data.remote.ScheduleEntryDto
import id.sch.santaagnes.sim.data.remote.ScheduleOriginalDto
import id.sch.santaagnes.sim.data.remote.SubjectDto
import id.sch.santaagnes.sim.data.repository.toDomain
import id.sch.santaagnes.sim.data.repository.toEntity
import id.sch.santaagnes.sim.domain.model.ChangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleMappersTest {

    private fun dto(
        change: ScheduleChangeDto? = null,
        original: ScheduleOriginalDto? = null
    ) = ScheduleEntryDto(
        date = "2026-11-11",
        slotId = 7,
        dayOfWeek = "WEDNESDAY",
        startTime = "07:00",
        endTime = "08:30",
        room = IdNameDto(3, "Ruang 101"),
        teacher = IdNameDto(2, "Guru Dua"),
        subject = SubjectDto(5, "MAT", "Matematika"),
        classGroup = IdNameDto(1, "X-1"),
        change = change,
        original = original
    )

    @Test
    fun mapsPlainLesson() {
        val entry = dto().toDomain()

        assertEquals(LocalDate.of(2026, 11, 11), entry.date)
        assertEquals(DayOfWeek.WEDNESDAY, entry.dayOfWeek)
        assertEquals(LocalTime.of(7, 0), entry.startTime)
        assertEquals(LocalTime.of(8, 30), entry.endTime)
        assertEquals("Matematika", entry.subjectName)
        assertEquals("Guru Dua", entry.teacherName)
        assertEquals("Ruang 101", entry.roomName)
        assertNull(entry.change)
        assertNull(entry.original)
    }

    @Test
    fun mapsSubstituteWithOriginalValues() {
        val entry = dto(
            change = ScheduleChangeDto(9, "SUBSTITUTE", "Guru berhalangan"),
            original = ScheduleOriginalDto("07:00", "08:30", IdNameDto(3, "Ruang 101"), IdNameDto(1, "Guru Satu"))
        ).toDomain()

        assertEquals(ChangeType.SUBSTITUTE, entry.change?.type)
        assertEquals("Guru berhalangan", entry.change?.description)
        assertEquals("Guru Satu", entry.original?.teacherName)
        assertEquals(LocalTime.of(7, 0), entry.original?.startTime)
    }

    @Test
    fun ignoresUnknownChangeTypeButKeepsLesson() {
        val entry = dto(
            change = ScheduleChangeDto(9, "TYPE_BARU", null),
            original = ScheduleOriginalDto("07:00", "08:30", null, null)
        ).toDomain()

        assertEquals("Matematika", entry.subjectName)
        assertNull(entry.change)
        assertNull(entry.original)
    }

    @Test
    fun roundTripsThroughRoomEntity() {
        val entry = dto(
            change = ScheduleChangeDto(9, "RESCHEDULE", "Dipindah"),
            original = ScheduleOriginalDto("06:30", "08:00", IdNameDto(4, "Ruang 202"), IdNameDto(2, "Guru Dua"))
        ).toDomain()

        val entity = entry.toEntity(accountId = 11, scope = 4)

        assertEquals(11, entity.accountId)
        assertEquals(4, entity.scope)
        assertEquals("2026-11-11", entity.date)
        assertEquals("RESCHEDULE", entity.changeType)
        assertEquals(entry, entity.toDomain())
    }
}
