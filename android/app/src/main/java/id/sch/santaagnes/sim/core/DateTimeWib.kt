package id.sch.santaagnes.sim.core

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Tanggal dan jam untuk tampilan. API mengirim tanggal `YYYY-MM-DD` dan jam `HH:mm` yang sudah
 * dalam WIB; nama hari/bulan ditulis sendiri agar tidak bergantung pada data locale perangkat.
 */
object DateTimeWib {

    val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    private val dayNames = mapOf(
        DayOfWeek.MONDAY to "Senin",
        DayOfWeek.TUESDAY to "Selasa",
        DayOfWeek.WEDNESDAY to "Rabu",
        DayOfWeek.THURSDAY to "Kamis",
        DayOfWeek.FRIDAY to "Jumat",
        DayOfWeek.SATURDAY to "Sabtu",
        DayOfWeek.SUNDAY to "Minggu"
    )

    private val monthNames = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    private val shortMonthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    fun today(clock: Clock = Clock.system(zone)): LocalDate = LocalDate.now(clock)

    /** Senin pekan berjalan; pada hari Minggu (tidak ada pelajaran) yang dipakai pekan depan. */
    fun currentWeekStart(today: LocalDate): LocalDate =
        if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            today.plusDays(1)
        } else {
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }

    /** Sabtu, hari terakhir jadwal pelajaran dalam satu pekan. */
    fun weekEnd(weekStart: LocalDate): LocalDate = weekStart.plusDays(5)

    fun dayName(day: DayOfWeek): String = dayNames.getValue(day)

    /** "Rabu, 11 November 2026". */
    fun formatDate(date: LocalDate): String =
        "${dayName(date.dayOfWeek)}, ${date.dayOfMonth} ${monthNames[date.monthValue - 1]} ${date.year}"

    /** "9 Nov - 14 Nov 2026" (tahun ditulis di kedua ujung bila rentang melewati pergantian tahun). */
    fun formatRange(start: LocalDate, end: LocalDate): String {
        val from = "${start.dayOfMonth} ${shortMonthNames[start.monthValue - 1]}" +
            if (start.year != end.year) " ${start.year}" else ""
        val to = "${end.dayOfMonth} ${shortMonthNames[end.monthValue - 1]} ${end.year}"
        return "$from – $to"
    }

    /** "11 Nov 2026, 14:05" dari waktu epoch (milidetik), dalam WIB. */
    fun formatTimestamp(epochMillis: Long): String {
        val time = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
        return "${time.dayOfMonth} ${shortMonthNames[time.monthValue - 1]} ${time.year}, " +
            formatTime(time.toLocalTime())
    }

    /** "07:00". */
    fun formatTime(time: LocalTime): String = time.toString().take(5)
}
