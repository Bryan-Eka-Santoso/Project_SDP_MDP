package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.data.repository.CalendarEntries
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEntriesTest {

    private fun booking(
        status: String = "APPROVED",
        start: String = "10:00",
        end: String = "12:00"
    ) = PeminjamanDto(
        id = 7,
        facilityId = 2,
        facilityName = "Aula",
        facilityType = "ROOM",
        requesterId = 5,
        requesterName = "Maria Lestari",
        bookingDate = "2026-11-25",
        startTime = start,
        endTime = end,
        purpose = "Rapat panitia",
        status = status,
        verifierName = "Administrator"
    )

    @Test
    fun `agenda akademik menjadi acara sepanjang hari dengan batas akhir eksklusif`() {
        val entry = CalendarEntries.fromAcademicEvent(
            AcademicEvent(3, "Class Meeting", AcademicEventType.ACTIVITY, "2026-12-14", "2026-12-17")
        )!!

        assertEquals("acad-3", entry.key)
        assertEquals("[Kegiatan] Class Meeting", entry.title)
        assertTrue(entry.allDay)
        // 14 s.d. 17 Desember = 4 hari, sehingga akhir eksklusif jatuh 4 hari penuh setelah awal.
        assertEquals(4 * 24 * 3_600_000L, entry.endMillis - entry.startMillis)
    }

    @Test
    fun `tanggal agenda yang rusak dilewati`() {
        assertNull(CalendarEntries.fromAcademicEvent(AcademicEvent(1, "X", AcademicEventType.EXAM, "bukan-tanggal", "2026-12-17")))
    }

    @Test
    fun `peminjaman disetujui ditulis dengan jam WIB`() {
        val entry = CalendarEntries.fromBooking(booking())!!

        assertEquals("book-7", entry.key)
        assertEquals("Peminjaman: Aula", entry.title)
        assertEquals("Aula", entry.location)
        assertFalse(entry.allDay)
        // 10:00 WIB = 03:00 UTC pada 2026-11-25.
        assertEquals(java.time.Instant.parse("2026-11-25T03:00:00Z").toEpochMilli(), entry.startMillis)
        assertEquals(2 * 3_600_000L, entry.endMillis - entry.startMillis)
    }

    @Test
    fun `peminjaman yang belum disetujui atau sudah selesai tidak masuk kalender`() {
        listOf("PENDING", "REJECTED", "RETURNED").forEach {
            assertNull(CalendarEntries.fromBooking(booking(status = it)))
        }
        assertNotNull(CalendarEntries.fromBooking(booking(status = "APPROVED")))
    }

    @Test
    fun `jam selesai yang tidak valid diganti satu jam setelah mulai`() {
        val entry = CalendarEntries.fromBooking(booking(start = "10:00", end = "10:00"))!!

        assertEquals(3_600_000L, entry.endMillis - entry.startMillis)
    }
}
