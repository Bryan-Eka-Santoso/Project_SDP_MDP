package id.sch.santaagnes.sim.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalendarSyncPlannerTest {

    private fun entry(key: String, title: String = "Agenda", start: Long = 1_000L) = CalendarEntry(
        key = key,
        title = title,
        description = null,
        location = null,
        startMillis = start,
        endMillis = start + 3_600_000L,
        allDay = false
    )

    @Test
    fun `agenda baru ditambahkan`() {
        val plan = CalendarSyncPlanner.plan(listOf(entry("acad-1")), emptyMap())

        assertEquals(listOf("acad-1"), plan.inserts.map { it.key })
        assertEquals(0, plan.updates.size)
        assertEquals(0, plan.deletes.size)
    }

    @Test
    fun `agenda yang tidak berubah dilewati`() {
        val e = entry("acad-1")
        val plan = CalendarSyncPlanner.plan(listOf(e), mapOf("acad-1" to SyncedRef(10, e.fingerprint)))

        assertEquals(0, plan.inserts.size)
        assertEquals(0, plan.updates.size)
        assertEquals(0, plan.deletes.size)
        assertEquals(1, plan.unchanged)
    }

    @Test
    fun `agenda yang isinya berubah diperbarui pada event yang sama`() {
        val old = entry("book-7", title = "Peminjaman: Aula")
        val changed = entry("book-7", title = "Peminjaman: Laboratorium")
        val plan = CalendarSyncPlanner.plan(listOf(changed), mapOf("book-7" to SyncedRef(55, old.fingerprint)))

        assertEquals(1, plan.updates.size)
        assertEquals(55L, plan.updates.single().second.eventId)
        assertEquals(0, plan.inserts.size)
    }

    @Test
    fun `agenda yang hilang dari server dihapus dari kalender`() {
        val gone = entry("book-9")
        val plan = CalendarSyncPlanner.plan(emptyList(), mapOf("book-9" to SyncedRef(77, gone.fingerprint)))

        assertEquals(listOf("book-9"), plan.deletes.map { it.first })
        assertEquals(77L, plan.deletes.single().second.eventId)
    }

    @Test
    fun `kunci ganda dari server hanya ditulis sekali`() {
        val plan = CalendarSyncPlanner.plan(listOf(entry("acad-1"), entry("acad-1")), emptyMap())

        assertEquals(1, plan.inserts.size)
    }

    @Test
    fun `sidik berubah bila waktu berubah`() {
        assertNotEquals(entry("a", start = 1_000L).fingerprint, entry("a", start = 2_000L).fingerprint)
    }
}
