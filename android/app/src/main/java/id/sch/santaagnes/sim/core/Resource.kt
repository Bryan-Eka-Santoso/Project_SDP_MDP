package id.sch.santaagnes.sim.core

import id.sch.santaagnes.sim.domain.model.ImportRowError

/** Hasil operasi repository: sukses membawa data, gagal membawa pesan siap tampil (bahasa Indonesia). */
sealed interface Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>

    /**
     * [importErrors] terisi hanya untuk impor Excel yang ditolak (`IMPORT_INVALID`): satu entri per
     * baris/kolom yang salah. [message] tetap memuat semuanya sebagai teks untuk layar yang tidak
     * merincinya.
     */
    data class Error(
        val message: String,
        val importErrors: List<ImportRowError> = emptyList()
    ) : Resource<Nothing>
}
