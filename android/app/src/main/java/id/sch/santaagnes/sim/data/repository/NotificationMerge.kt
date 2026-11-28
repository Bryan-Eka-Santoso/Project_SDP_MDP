package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.Rupiah
import id.sch.santaagnes.sim.data.local.NotificationEntity
import id.sch.santaagnes.sim.data.remote.BillDto

/** Keputusan untuk notifikasi baru terhadap baris berjenis, berefensi, dan berjudul sama. */
internal sealed interface NotificationWrite {
    data object Skip : NotificationWrite
    data class Insert(val entity: NotificationEntity) : NotificationWrite
    data class Update(val entity: NotificationEntity) : NotificationWrite
}

/**
 * Push hanya dibuang bila persis dikirim ulang: perubahan jadwal yang diedit memakai referensi yang
 * sama. Baris hasil sinkron untuk kejadian itu digantikan push agar tidak dobel.
 */
internal fun mergePush(existing: List<NotificationEntity>, incoming: NotificationEntity): NotificationWrite {
    if (existing.any { !it.isSynced && it.message == incoming.message }) return NotificationWrite.Skip
    val synced = existing.firstOrNull { it.isSynced } ?: return NotificationWrite.Insert(incoming)
    return NotificationWrite.Update(
        incoming.copy(id = synced.id, isRead = synced.isRead && synced.message == incoming.message)
    )
}

/** Push yang sudah diterima diutamakan; baris hasil sinkron diperbarui hanya bila isinya berubah. */
internal fun mergeSynced(existing: List<NotificationEntity>, incoming: NotificationEntity): NotificationWrite {
    if (existing.isEmpty()) return NotificationWrite.Insert(incoming)
    if (existing.any { !it.isSynced }) return NotificationWrite.Skip
    val current = existing.first()
    return if (current.message == incoming.message) {
        NotificationWrite.Skip
    } else {
        NotificationWrite.Update(incoming.copy(id = current.id, isRead = false))
    }
}

private const val MAX_OVERDUE_DETAILS = 3

/** Satu ringkasan tunggakan per siswa; susunannya sama dengan isi push dari server (F-BYR-03). */
internal fun overdueSummary(
    name: String,
    bills: List<BillDto>,
    totalCount: Int,
    formatDate: (String) -> String
): String {
    val sorted = bills.sortedBy { it.dueDate }
    val single = sorted.singleOrNull()
    if (single != null && totalCount <= 1) {
        return "$name: tagihan ${single.paymentType} (${single.billCode}) sebesar " +
            "${Rupiah.format(single.amount)} jatuh tempo ${formatDate(single.dueDate)} dan belum dibayar."
    }
    val details = sorted.take(MAX_OVERDUE_DETAILS).joinToString("; ") {
        "${it.paymentType} ${Rupiah.format(it.amount)} (jatuh tempo ${formatDate(it.dueDate)})"
    }
    val rest = totalCount - minOf(sorted.size, MAX_OVERDUE_DETAILS)
    val more = if (rest > 0) "; dan $rest lainnya" else ""
    return "$name: $totalCount tagihan belum dibayar dan sudah lewat jatuh tempo, " +
        "total ${Rupiah.format(sorted.sumOf { it.amount })}. Rincian: $details$more."
}
