package id.sch.santaagnes.sim.notification

import id.sch.santaagnes.sim.data.local.NotificationEntity
import id.sch.santaagnes.sim.data.remote.BillDto
import id.sch.santaagnes.sim.data.repository.NotificationWrite
import id.sch.santaagnes.sim.data.repository.mergePush
import id.sch.santaagnes.sim.data.repository.mergeSynced
import id.sch.santaagnes.sim.data.repository.overdueSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMergeTest {

    private fun row(message: String, synced: Boolean, id: Long = 0, read: Boolean = false) = NotificationEntity(
        id = id, accountId = 1, title = "Perubahan jadwal", message = message,
        type = "SCHEDULE_CHANGE", referenceId = "31", receivedAt = 1000, isRead = read, isSynced = synced
    )

    @Test
    fun redeliveredPushIsIgnored() {
        val result = mergePush(listOf(row("dibatalkan", synced = false, id = 4)), row("dibatalkan", synced = false))

        assertEquals(NotificationWrite.Skip, result)
    }

    @Test
    fun editedChangeWithSameReferenceIsStoredAsNewNotification() {
        val incoming = row("dipindah ke 10.00", synced = false)

        val result = mergePush(listOf(row("dibatalkan", synced = false, id = 4)), incoming)

        assertEquals(NotificationWrite.Insert(incoming), result)
    }

    @Test
    fun pushReplacesTheSyncedCopyInsteadOfDoublingIt() {
        val result = mergePush(listOf(row("versi sinkron", synced = true, id = 7)), row("versi push", synced = false))

        assertEquals(NotificationWrite.Update(row("versi push", synced = false, id = 7)), result)
    }

    @Test
    fun syncDoesNotDuplicateAReceivedPush() {
        val result = mergeSynced(listOf(row("versi push", synced = false, id = 4)), row("versi sinkron", synced = true))

        assertEquals(NotificationWrite.Skip, result)
    }

    @Test
    fun syncRefreshesItsOwnRowWhenTheEventChanged() {
        val result = mergeSynced(
            listOf(row("lama", synced = true, id = 7, read = true)),
            row("baru", synced = true)
        )

        assertEquals(NotificationWrite.Update(row("baru", synced = true, id = 7)), result)
    }

    @Test
    fun syncLeavesAnUnchangedRowAlone() {
        val result = mergeSynced(listOf(row("sama", synced = true, id = 7, read = true)), row("sama", synced = true))

        assertEquals(NotificationWrite.Skip, result)
    }

    @Test
    fun overdueSummaryListsThreeOldestBillsAndTheRest() {
        val bills = (5 downTo 1).map { month ->
            BillDto(
                id = month, billCode = "SPP-$month", paymentType = "SPP $month", amount = 100_000,
                dueDate = "2026-0$month-10", status = "UNPAID", paidDate = null, isOverdue = true
            )
        }

        val text = overdueSummary("Angela", bills, totalCount = 5) { it }

        assertTrue(text, text.startsWith("Angela: 5 tagihan belum dibayar"))
        assertTrue(text, text.contains("total IDR 500.000"))
        assertTrue(
            text,
            text.contains(
                "SPP 1 IDR 100.000 (jatuh tempo 2026-01-10); SPP 2 IDR 100.000 (jatuh tempo 2026-02-10); " +
                    "SPP 3 IDR 100.000 (jatuh tempo 2026-03-10); dan 2 lainnya."
            )
        )
    }
}
