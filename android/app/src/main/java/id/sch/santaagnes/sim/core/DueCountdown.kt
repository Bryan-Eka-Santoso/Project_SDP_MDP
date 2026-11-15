package id.sch.santaagnes.sim.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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

/** Jam tenggat bila tugas lama tidak punya jam (sebelum kolom jam tenggat ada): akhir hari. */
val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(23, 59)

/** Batas sisa waktu yang ditandai merah di daftar tugas. */
private val URGENT_WINDOW: Duration = Duration.ofHours(24)

/**
 * Tenggat dianggap mendesak (ditampilkan merah) bila sudah lewat atau tinggal 24 jam atau kurang.
 * `time` null berarti tugas lama tanpa jam, dihitung sampai 23:59.
 */
fun isDueUrgent(date: LocalDate, time: LocalTime?, now: LocalDateTime): Boolean {
    val due = LocalDateTime.of(date, time ?: DEFAULT_DUE_TIME)
    return Duration.between(now, due) <= URGENT_WINDOW
}
