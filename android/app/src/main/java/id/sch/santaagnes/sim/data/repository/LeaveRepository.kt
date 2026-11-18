package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.CreateLeaveDto
import id.sch.santaagnes.sim.data.remote.LeaveApi
import id.sch.santaagnes.sim.data.remote.LeaveChildDto
import id.sch.santaagnes.sim.data.remote.LeaveRequestDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class LeaveRepository @Inject constructor(private val api: LeaveApi) {
    suspend fun children(): List<LeaveChildDto> = api.children().data.orEmpty()
    suspend fun list(childId: Int): List<LeaveRequestDto> = api.list(childId).data.orEmpty()
    suspend fun create(childId: Int, start: String, end: String, reason: String,
                       file: ByteArray? = null, fileName: String? = null, mime: String? = null) {
        val result = if (file != null && fileName != null && mime != null) {
            val binary = file.toRequestBody(mime.toMediaType())
            val part = MultipartBody.Part.createFormData("attachment", fileName, binary)
            fun field(value: String) = value.toRequestBody("text/plain".toMediaType())
            api.createWithAttachment(field(childId.toString()), field(start), field(end), field(reason), part)
        } else api.create(CreateLeaveDto(childId, start, end, reason))
        if (!result.success) error("Pengajuan izin gagal.")
    }
    companion object {
        fun message(error: Throwable): String = when (error) {
            is IOException -> "Tidak bisa terhubung ke backend."
            is HttpException -> when (error.code()) {
                400 -> "Data izin tidak valid. Periksa tanggal, alasan, dan lampiran."
                413 -> "Ukuran lampiran maksimal 5 MB."
                415 -> "Lampiran harus berupa JPG, PNG, atau PDF."
                401 -> "Sesi login sudah habis. Silakan login kembali."
                403, 404 -> "Anak tidak terhubung ke akun wali murid ini."
                else -> "Gagal mengakses server (HTTP ${error.code()})."
            }
            else -> error.message ?: "Terjadi kesalahan."
        }
    }
}
