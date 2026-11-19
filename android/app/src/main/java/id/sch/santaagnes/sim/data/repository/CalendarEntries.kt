package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.CalendarEntry
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Pemetaan data server ke agenda kalender perangkat (F-JDW-06). */
object CalendarEntries {

    const val ACADEMIC_PREFIX = "acad-"
    const val BOOKING_PREFIX = "book-"

    /** Agenda sepanjang hari; batas akhir eksklusif dan zona UTC sesuai aturan CalendarContract. */
    fun fromAcademicEvent(event: AcademicEvent): CalendarEntry? {
        val start = runCatching { LocalDate.parse(event.startDate) }.getOrNull() ?: return null
        val end = runCatching { LocalDate.parse(event.endDate) }.getOrNull()?.takeIf { !it.isBefore(start) } ?: start
        return CalendarEntry(
            key = ACADEMIC_PREFIX + event.id,
            title = "[${event.type.label}] ${event.title}",
            description = event.description?.takeIf { it.isNotBlank() },
            location = null,
            startMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endMillis = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            allDay = true
        )
    }

    /** Hanya peminjaman yang disetujui dan belum dikembalikan; lainnya tidak masuk kalender. */
    fun fromBooking(booking: PeminjamanDto): CalendarEntry? {
        if (booking.status != STATUS_APPROVED) return null
        val date = runCatching { LocalDate.parse(booking.bookingDate) }.getOrNull() ?: return null
        val startTime = runCatching { LocalTime.parse(booking.startTime) }.getOrNull() ?: return null
        val endTime = runCatching { LocalTime.parse(booking.endTime) }.getOrNull() ?: return null

        val start = date.atTime(startTime).atZone(DateTimeWib.zone).toInstant().toEpochMilli()
        var end = date.atTime(endTime).atZone(DateTimeWib.zone).toInstant().toEpochMilli()
        if (end <= start) end = start + ONE_HOUR_MILLIS
        return CalendarEntry(
            key = BOOKING_PREFIX + booking.id,
            title = "Peminjaman: ${booking.facilityName}",
            description = booking.purpose.takeIf { it.isNotBlank() },
            location = booking.facilityName,
            startMillis = start,
            endMillis = end,
            allDay = false
        )
    }

    private const val STATUS_APPROVED = "APPROVED"
    private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
}
