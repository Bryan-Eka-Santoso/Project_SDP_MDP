package id.sch.santaagnes.sim.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

data class SiswaRiwayatDto(
    val id: Int,
    val nis: String,
    val name: String,
    val className: String
)

data class KejadianPelanggaranDto(
    val id: Int,
    val name: String,
    val date: String,
    val points: Int,
    val description: String?,
    val reporterName: String
)

data class RiwayatPelanggaranDto(
    val student: SiswaRiwayatDto,
    val totalPoints: Int,
    val totalViolations: Int,
    val violations: List<KejadianPelanggaranDto>
)

interface RiwayatPelanggaranApi {

    @GET("violation-history/students")
    suspend fun getSiswa():
            ApiResponse<List<SiswaRiwayatDto>>

    @GET("violation-history/students/{studentId}")
    suspend fun getRiwayat(
        @Path("studentId") studentId: Int
    ): ApiResponse<RiwayatPelanggaranDto>
}