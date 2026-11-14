package id.sch.santaagnes.sim.domain.model

/**
 * Satu kesalahan pada impor Excel yang ditolak (`IMPORT_INVALID`). [row] mengikuti nomor baris di
 * Excel (header = 1), null bila kesalahan menyangkut seluruh berkas; [column] null bila menyangkut
 * seluruh baris.
 */
data class ImportRowError(
    val row: Int?,
    val column: String?,
    val message: String
)
