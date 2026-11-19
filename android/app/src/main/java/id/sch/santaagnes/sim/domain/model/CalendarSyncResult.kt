package id.sch.santaagnes.sim.domain.model

/** Ringkasan satu kali sinkron ke kalender perangkat (F-JDW-06). */
data class CalendarSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    // Jumlah agenda dari aplikasi yang kini ada di kalender perangkat.
    val total: Int
)
