package id.sch.santaagnes.sim.domain.model

/** Ringkasan hasil impor data master dari Excel (F-MST-04 Siswa, F-MST-05 Guru). */
data class MasterImportResult(
    val imported: Int,
    val assignedToClass: Int = 0
)
