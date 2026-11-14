package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.remote.IdNameDto
import id.sch.santaagnes.sim.data.remote.ReportDayLoadDto
import id.sch.santaagnes.sim.data.remote.ReportTeacherDto
import id.sch.santaagnes.sim.data.remote.ScheduleChangeReportDto
import id.sch.santaagnes.sim.data.remote.SubjectDto
import id.sch.santaagnes.sim.data.remote.TeachingLoadDto
import id.sch.santaagnes.sim.data.repository.toDomain
import id.sch.santaagnes.sim.domain.model.ChangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ReportMappersTest {

    private fun changeDto(type: String) = ScheduleChangeReportDto(
        id = 8,
        date = "2026-11-20",
        dayOfWeek = "FRIDAY",
        classGroup = IdNameDto(5, "X-1"),
        subject = SubjectDto(4, "FIS", "Fisika"),
        originalTeacher = IdNameDto(3, "Guru Satu"),
        type = type,
        substituteTeacher = null,
        originalRoom = IdNameDto(2, "Ruang X-1"),
        originalStartTime = "10:30",
        originalEndTime = "12:00",
        newRoom = IdNameDto(9, "Aula"),
        newStartTime = "13:00",
        newEndTime = "14:30",
        description = "Pindah ke siang."
    )

    @Test
    fun mapsRescheduleChange() {
        val row = changeDto("RESCHEDULE").toDomain()

        assertEquals(LocalDate.of(2026, 11, 20), row.date)
        assertEquals(DayOfWeek.FRIDAY, row.dayOfWeek)
        assertEquals(ChangeType.RESCHEDULE, row.type)
        assertEquals("Fisika", row.subjectName)
        assertEquals("X-1", row.classGroupName)
        assertEquals("Guru Satu", row.originalTeacherName)
        assertEquals("Ruang X-1", row.originalRoomName)
        assertEquals("Aula", row.newRoomName)
        assertEquals(LocalTime.of(10, 30), row.originalStart)
        assertEquals(LocalTime.of(13, 0), row.newStart)
        assertEquals(LocalTime.of(14, 30), row.newEnd)
        assertNull(row.substituteTeacherName)
    }

    @Test
    fun mapsCancelWithoutNewValues() {
        val dto = changeDto("CANCEL").copy(newRoom = null, newStartTime = null, newEndTime = null)

        val row = dto.toDomain()

        assertEquals(ChangeType.CANCEL, row.type)
        assertNull(row.newRoomName)
        assertNull(row.newStart)
        assertNull(row.newEnd)
    }

    @Test
    fun mapsTeachingLoadHoursPerDayFromMinutes() {
        val dto = TeachingLoadDto(
            teacher = ReportTeacherDto(3, "Guru Satu", "198500000001"),
            sessionsPerWeek = 3,
            totalMinutes = 270.0,
            totalHours = 4.5,
            classCount = 2,
            subjects = listOf(SubjectDto(4, "MTK", "Matematika")),
            perDay = mapOf(
                "MONDAY" to ReportDayLoadDto(2, 180.0),
                "WEDNESDAY" to ReportDayLoadDto(1, 90.0)
            )
        )

        val row = dto.toDomain()

        assertEquals("Guru Satu", row.teacherName)
        assertEquals("198500000001", row.employeeNumber)
        assertEquals(4.5, row.totalHours, 0.0)
        assertEquals(listOf("Matematika"), row.subjectNames)
        // Senin..Sabtu; hari tanpa jadwal = 0 jam.
        assertEquals(listOf(3.0, 0.0, 1.5, 0.0, 0.0, 0.0), row.hoursPerDay)
    }

    @Test
    fun teacherWithoutScheduleHasZeroHours() {
        val dto = TeachingLoadDto(
            teacher = ReportTeacherDto(9, "Guru Baru", null),
            sessionsPerWeek = 0,
            totalMinutes = 0.0,
            totalHours = 0.0,
            classCount = 0,
            subjects = emptyList(),
            perDay = emptyMap()
        )

        val row = dto.toDomain()

        assertNull(row.employeeNumber)
        assertEquals(0, row.sessionsPerWeek)
        assertEquals(List(6) { 0.0 }, row.hoursPerDay)
    }
}
