package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class SiswaPelanggaranDto(
    val id: Int,
    val nis: String,
    val name: String,
    val className: String
)

data class JenisPelanggaranGuruDto(
    val id: Int,
    val name: String,
    val points: Int
)

data class InputPelanggaranRequest(
    val studentId: Int,
    val violationTypeId: Int,
    val date: String,
    val description: String?
)

data class HasilPelanggaranDto(
    val id: Int,
    val studentId: Int,
    val studentName: String,
    val violationTypeId: Int,
    val violationName: String,
    val points: Int,
    val date: String,
    val description: String?,
    val guardianCount: Int
)

interface PelanggaranApi {

    @GET("violations/students")
    suspend fun getSiswa():
            ApiResponse<List<SiswaPelanggaranDto>>

    @GET("violations/types")
    suspend fun getJenis():
            ApiResponse<List<JenisPelanggaranGuruDto>>

    @POST("violations")
    suspend fun simpan(
        @Body body: InputPelanggaranRequest
    ): ApiResponse<HasilPelanggaranDto>
}