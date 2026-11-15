package id.sch.santaagnes.sim.domain.model

/** Jumlah seluruh data per jenis untuk dashboard admin; null bila jenis itu gagal dimuat. */
data class AdminOverview(
    val students: Int? = null,
    val teachers: Int? = null,
    val classes: Int? = null,
    val users: Int? = null,
    val subjects: Int? = null,
    val scheduleSlots: Int? = null,
    val scheduleChanges: Int? = null,
    val facilities: Int? = null
)
