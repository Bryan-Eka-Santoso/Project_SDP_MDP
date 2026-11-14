package id.sch.santaagnes.sim.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DateTimeWibTest {

    @Test
    fun weekStartIsMondayOfTheSameWeek() {
        val monday = LocalDate.of(2026, 11, 9)

        assertEquals(monday, DateTimeWib.currentWeekStart(LocalDate.of(2026, 11, 9)))
        assertEquals(monday, DateTimeWib.currentWeekStart(LocalDate.of(2026, 11, 11)))
        assertEquals(monday, DateTimeWib.currentWeekStart(LocalDate.of(2026, 11, 14)))
    }

    @Test
    fun sundayShowsTheUpcomingWeek() {
        assertEquals(
            LocalDate.of(2026, 11, 16),
            DateTimeWib.currentWeekStart(LocalDate.of(2026, 11, 15))
        )
    }

    @Test
    fun weekEndIsSaturday() {
        assertEquals(LocalDate.of(2026, 11, 14), DateTimeWib.weekEnd(LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun formatsDateInIndonesian() {
        assertEquals("Rabu, 11 November 2026", DateTimeWib.formatDate(LocalDate.of(2026, 11, 11)))
        assertEquals("Senin, 1 Juni 2026", DateTimeWib.formatDate(LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun formatsWeekRangeAndYearBoundary() {
        assertEquals(
            "9 Nov – 14 Nov 2026",
            DateTimeWib.formatRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 14))
        )
        assertEquals(
            "28 Des 2026 – 2 Jan 2027",
            DateTimeWib.formatRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 2))
        )
    }

    @Test
    fun formatsTimestampInWib() {
        // 2026-11-12T01:05:00Z = 08:05 WIB
        val millis = java.time.Instant.parse("2026-11-12T01:05:00Z").toEpochMilli()

        assertEquals("12 Nov 2026, 08:05", DateTimeWib.formatTimestamp(millis))
    }

    @Test
    fun formatsTimeWithoutSeconds() {
        assertEquals("07:00", DateTimeWib.formatTime(LocalTime.of(7, 0)))
        assertEquals("13:45", DateTimeWib.formatTime(LocalTime.of(13, 45, 30)))
    }
}
