package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.HomeroomLeaveDto
import id.sch.santaagnes.sim.data.remote.HomeroomDecisionRequest
import id.sch.santaagnes.sim.data.remote.LeaveApi
import id.sch.santaagnes.sim.data.remote.PageMetaDto
import retrofit2.HttpException
import java.io.IOException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PendingLeavePage(val items: List<HomeroomLeaveDto>, val meta: PageMetaDto?)

class HomeroomLeaveRepository @Inject constructor(private val api: LeaveApi) {
    suspend fun pending(page: Int): PendingLeavePage {
        val result = api.pendingHomeroom(page = page)
        if (!result.success) error(result.error?.message ?: "Gagal memuat pengajuan izin.")
        return PendingLeavePage(result.data.orEmpty(), result.meta)
    }

    suspend fun decide(requestId: Int, decision: String) {
        require(decision == "APPROVED" || decision == "REJECTED")
        val result = api.decideHomeroom(requestId, HomeroomDecisionRequest(decision))
        if (!result.success) error(result.error?.message ?: "Keputusan izin gagal disimpan.")
    }

    suspend fun downloadProof(requestId: Int, target: File): Unit = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        try {
            api.downloadHomeroomAttachment(requestId).use { response ->
                response.byteStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    companion object {
        fun friendlyError(error: Throwable): String = when (error) {
            is IOException -> "Tidak dapat terhubung ke server. Periksa jaringan dan backend."
            is HttpException -> when (error.code()) {
                401 -> "Sesi login berakhir. Silakan login kembali."
                403 -> "Akses hanya tersedia untuk guru wali kelas."
                400 -> "Permintaan keputusan izin tidak valid."
                404 -> "Pengajuan tidak ditemukan atau bukan siswa kelas Anda."
                409 -> "Pengajuan ini sudah diproses. Muat ulang daftar."
                else -> "Permintaan gagal (HTTP ${error.code()})."
            }
            else -> error.message ?: "Terjadi kesalahan."
        }
    }
}
