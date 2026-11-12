package id.sch.santaagnes.sim.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Sisa waktu menuju tenggat (per hari kalender WIB; tenggat berupa tanggal, bukan jam). */
sealed interface DueCountdown {
    data class Overdue(val days: Int) : DueCountdown
    data object Today : DueCountdown
    data class Remaining(val days: Int) : DueCountdown
}

fun dueCountdown(due: LocalDate, today: LocalDate): DueCountdown {
    val days = ChronoUnit.DAYS.between(today, due).toInt()
    return when {
        days < 0 -> DueCountdown.Overdue(-days)
        days == 0 -> DueCountdown.Today
        else -> DueCountdown.Remaining(days)
    }
}
