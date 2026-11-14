package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.CreateLeaveDto
import id.sch.santaagnes.sim.data.remote.LeaveApi
import id.sch.santaagnes.sim.data.remote.LeaveChildDto
import id.sch.santaagnes.sim.data.remote.LeaveRequestDto
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class LeaveRepository @Inject constructor(private val api: LeaveApi) {
    suspend fun children(): List<LeaveChildDto> = api.children().data.orEmpty()
    suspend fun list(childId: Int): List<LeaveRequestDto> = api.list(childId).data.orEmpty()
    suspend fun create(childId: Int, start: String, end: String, reason: String) {
        val result = api.create(CreateLeaveDto(childId, start, end, reason))
        if (!result.success) error("Pengajuan izin gagal.")
    }
    companion object {
        fun message(error: Throwable): String = when (error) {
            is IOException -> "Tidak bisa terhubung ke backend."
            is HttpException -> when (error.code()) {
                400 -> "Data izin tidak valid. Periksa tanggal dan alasan."
                401 -> "Sesi login sudah habis. Silakan login kembali."
                403, 404 -> "Anak tidak terhubung ke akun wali murid ini."
                else -> "Gagal mengakses server (HTTP ${error.code()})."
            }
            else -> error.message ?: "Terjadi kesalahan."
        }
    }
}
