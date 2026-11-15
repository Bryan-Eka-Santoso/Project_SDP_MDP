package id.sch.santaagnes.sim.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DueCountdownTest {

    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun futureDueDateCountsDaysRemaining() {
        assertEquals(DueCountdown.Remaining(1), dueCountdown(LocalDate.of(2026, 11, 12), today))
        assertEquals(DueCountdown.Remaining(20), dueCountdown(LocalDate.of(2026, 12, 1), today))
    }

    @Test
    fun sameDayIsToday() {
        assertEquals(DueCountdown.Today, dueCountdown(today, today))
    }

    @Test
    fun pastDueDateCountsDaysOverdue() {
        assertEquals(DueCountdown.Overdue(1), dueCountdown(LocalDate.of(2026, 11, 10), today))
        assertEquals(DueCountdown.Overdue(11), dueCountdown(LocalDate.of(2026, 10, 31), today))
    }

    private val now = LocalDateTime.of(2026, 11, 15, 10, 0)

    @Test
    fun dueWithin24HoursIsUrgent() {
        // tepat 24 jam lagi masih merah
        assertTrue(isDueUrgent(LocalDate.of(2026, 11, 16), LocalTime.of(10, 0), now))
        assertTrue(isDueUrgent(LocalDate.of(2026, 11, 15), LocalTime.of(18, 0), now))
    }

    @Test
    fun dueMoreThan24HoursAwayIsNotUrgent() {
        assertFalse(isDueUrgent(LocalDate.of(2026, 11, 16), LocalTime.of(10, 1), now))
        assertFalse(isDueUrgent(LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), now))
    }

    @Test
    fun overdueIsUrgent() {
        assertTrue(isDueUrgent(LocalDate.of(2026, 11, 15), LocalTime.of(9, 59), now))
        assertTrue(isDueUrgent(LocalDate.of(2026, 11, 1), LocalTime.of(12, 0), now))
    }

    @Test
    fun missingTimeCountsUntilEndOfDay() {
        // 23:59 besok pagi lebih dari 24 jam dari 10:00 hari ini
        assertFalse(isDueUrgent(LocalDate.of(2026, 11, 16), null, now))
        assertTrue(isDueUrgent(LocalDate.of(2026, 11, 15), null, now))
    }
}
