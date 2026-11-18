package id.sch.santaagnes.sim.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

data class SiswaBacaBukuDto(
    val id: Int,
    val nis: String,
    val name: String,
    val className: String
)

data class CatatanBacaBukuDto(
    val id: Int,
    val date: String,
    val content: String,
    val teacherName: String
)

data class RiwayatBukuPenghubungDto(
    val student: SiswaBacaBukuDto,
    val total: Int,
    val notes: List<CatatanBacaBukuDto>
)

interface BacaBukuPenghubungApi {

    @GET("liaison-history/students")
    suspend fun getSiswa():
            ApiResponse<List<SiswaBacaBukuDto>>

    @GET("liaison-history/students/{studentId}/notes")
    suspend fun getCatatan(
        @Path("studentId") studentId: Int
    ): ApiResponse<RiwayatBukuPenghubungDto>
}
