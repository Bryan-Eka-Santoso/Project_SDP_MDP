package id.sch.santaagnes.sim.core

import java.security.MessageDigest

/** Satu agenda yang akan ditulis ke kalender perangkat (F-JDW-06). */
data class CalendarEntry(
    // Kunci stabil dari sumber data, mis. "acad-12" atau "book-7"; dipakai mencocokkan sinkron ulang.
    val key: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
) {
    /** Berubah bila ada isi agenda yang berubah, supaya hanya agenda itu yang diperbarui. */
    val fingerprint: String
        get() {
            val joined = listOf(title, description.orEmpty(), location.orEmpty(), startMillis, endMillis, allDay)
                .joinToString("|")
            return MessageDigest.getInstance("SHA-256")
                .digest(joined.toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }
        }
}

/** Agenda yang pernah ditulis aplikasi: id event di kalender perangkat dan sidik isinya. */
data class SyncedRef(val eventId: Long, val fingerprint: String)

data class CalendarSyncPlan(
    val inserts: List<CalendarEntry>,
    val updates: List<Pair<CalendarEntry, SyncedRef>>,
    val deletes: List<Pair<String, SyncedRef>>,
    val unchanged: Int
)

/** Logika murni pencocokan agenda; tidak menyentuh kalender perangkat. */
object CalendarSyncPlanner {

    fun plan(desired: List<CalendarEntry>, synced: Map<String, SyncedRef>): CalendarSyncPlan {
        val desiredByKey = desired.associateBy { it.key }
        val inserts = mutableListOf<CalendarEntry>()
        val updates = mutableListOf<Pair<CalendarEntry, SyncedRef>>()
        var unchanged = 0

        for (entry in desiredByKey.values) {
            val ref = synced[entry.key]
            when {
                ref == null -> inserts += entry
                ref.fingerprint != entry.fingerprint -> updates += entry to ref
                else -> unchanged += 1
            }
        }
        // Agenda yang tidak lagi ada di server (dihapus, dibatalkan, dikembalikan) ikut dibuang.
        val deletes = synced.filterKeys { it !in desiredByKey }.map { (key, ref) -> key to ref }
        return CalendarSyncPlan(inserts, updates, deletes, unchanged)
    }
}
