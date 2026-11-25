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
    // ABS-03: pengambilan rekap bulanan siswa yang sedang login.
    suspend fun mySummary(startDate: String, endDate: String): id.sch.santaagnes.sim.data.remote.AttendanceSummaryDto {
        val response = api.myAttendanceSummary(startDate, endDate)
        if (!response.success) error(response.error?.message ?: "Gagal mengambil rekap presensi.")
        return response.data ?: error("Rekap presensi tidak tersedia.")
    }

    /** Rekap bulanan anak untuk wali murid; backend memeriksa bahwa anak itu terhubung ke wali. */
    suspend fun studentSummary(
        studentId: Int,
        startDate: String,
        endDate: String
    ): id.sch.santaagnes.sim.data.remote.AttendanceSummaryDto {
        val response = api.studentAttendanceSummary(studentId, startDate, endDate)
        if (!response.success) error(response.error?.message ?: "Gagal mengambil rekap presensi.")
        return response.data ?: error("Rekap presensi tidak tersedia.")
    }

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
            is IOException -> "Tidak dapat terhubung ke server. Periksa koneksi internet Anda."
            is HttpException -> when (e.code()) {
                400 -> "Tanggal, daftar siswa, atau jurnal tidak valid. Pastikan tanggal sesuai hari jadwal."
                401 -> "Sesi login Anda telah berakhir. Silakan masuk kembali."
                403 -> "Anda tidak dapat mengisi sesi ini (jadwal mungkin dibatalkan atau diganti)."
                404 -> "Jadwal tidak ditemukan atau bukan jadwal Anda."
                409 -> "Data mengalami konflik. Silakan muat ulang terlebih dahulu."
                else -> "Terjadi kesalahan pada server. Silakan coba lagi."
            }
            else -> e.message ?: "Terjadi kesalahan."
        }
    }
}
