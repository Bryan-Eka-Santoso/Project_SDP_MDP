package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.AttendanceApi
import id.sch.santaagnes.sim.data.remote.AttendanceInputDto
import id.sch.santaagnes.sim.data.remote.AttendanceSessionDto
import id.sch.santaagnes.sim.data.remote.AttendanceSlotDto
import id.sch.santaagnes.sim.data.remote.SaveAttendanceRequest
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class AttendanceRepository @Inject constructor(private val api: AttendanceApi) {
    suspend fun slots(assignmentId: Int): List<AttendanceSlotDto> =
        api.slots().data.orEmpty().filter { it.assignmentId == assignmentId }

    suspend fun session(slotId: Int, date: String): AttendanceSessionDto =
        api.session(slotId, date).data ?: error("Data sesi tidak tersedia.")

    suspend fun save(slotId: Int, date: String, journal: String, statuses: Map<Int, String>) {
        val response = api.save(slotId, SaveAttendanceRequest(
            date = date,
            journal = journal,
            attendances = statuses.map { (id, value) -> AttendanceInputDto(id, value) }
        ))
        if (!response.success) error("Gagal menyimpan presensi.")
    }

    companion object {
        fun errorMessage(e: Throwable): String = when (e) {
            is IOException -> "Tidak dapat menghubungi server."
            is HttpException -> when (e.code()) {
                400 -> "Tanggal, daftar siswa, atau jurnal tidak valid. Pastikan tanggal sesuai hari jadwal."
                401 -> "Sesi login habis. Silakan login ulang."
                403 -> "Kamu tidak boleh mengisi sesi ini (mungkin jadwal dibatalkan atau diganti)."
                404 -> "Jadwal tidak ditemukan atau bukan jadwalmu."
                409 -> "Data mengalami konflik. Muat ulang terlebih dahulu."
                else -> "Server bermasalah (HTTP ${e.code()})."
            }
            else -> e.message ?: "Terjadi kesalahan."
        }
    }
}
