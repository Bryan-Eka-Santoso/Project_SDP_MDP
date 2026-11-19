package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.*
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class ProgressNoteRepository @Inject constructor(private val api: ProgressNoteApi) {
    private fun <T> unwrap(response: ApiResponse<T>): T =
        if (!response.success) error(response.error?.message ?: "Gagal memuat catatan perkembangan.")
        else response.data ?: error("Data catatan tidak tersedia.")

    suspend fun mine(page: Int = 1) = api.mine(page).let { Pair(unwrap(it), it.meta) }
    suspend fun assignment(assignmentId: Int, page: Int = 1) =
        api.assignment(assignmentId, page).let { Pair(unwrap(it), it.meta) }
    suspend fun create(assignmentId: Int, studentId: Int, date: String, content: String) =
        unwrap(api.create(CreateProgressNoteRequest(assignmentId, studentId, date, content)))
    suspend fun update(noteId: Int, date: String, content: String) =
        unwrap(api.update(noteId, UpdateProgressNoteRequest(date, content)))

    companion object {
        fun message(e: Throwable): String = when(e) {
            is IOException -> "Tidak bisa terhubung ke server."
            is HttpException -> when(e.code()) {
                400 -> "Catatan harus diisi (maksimal 500 karakter), dan tanggal harus valid."
                401 -> "Sesi login habis. Silakan login ulang."
                403 -> "Kamu tidak punya akses ke catatan ini."
                404 -> "Penugasan atau siswa tidak ditemukan."
                else -> "Server bermasalah (HTTP ${e.code()})."
            }
            else -> e.message ?: "Terjadi kesalahan."
        }
    }
}
