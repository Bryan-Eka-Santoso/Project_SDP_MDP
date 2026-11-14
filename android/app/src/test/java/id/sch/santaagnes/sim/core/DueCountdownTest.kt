package id.sch.santaagnes.sim.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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
}
