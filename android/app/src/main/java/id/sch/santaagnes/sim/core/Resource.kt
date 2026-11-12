package id.sch.santaagnes.sim.core

/** Hasil operasi repository: sukses membawa data, gagal membawa pesan siap tampil (bahasa Indonesia). */
sealed interface Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>
    data class Error(val message: String) : Resource<Nothing>
}
