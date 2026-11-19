
package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class KelasPerwalianDto(
    val id: Int,
    val name: String,
    val gradeLevel: String
)

data class SiswaBukuPenghubungDto(
    val id: Int,
    val nis: String,
    val name: String
)

data class CatatanBukuPenghubungRequest(
    val classId: Int,
    val studentId: Int,
    val date: String,
    val content: String
)

data class CatatanBukuPenghubungDto(
    val id: Int,
    val studentId: Int,
    val studentName: String,
    val nis: String,
    val classId: Int,
    val className: String,
    val date: String,
    val content: String
)

interface BukuPenghubungApi {

    @GET("liaison-notes/classes")
    suspend fun getKelas():
            ApiResponse<List<KelasPerwalianDto>>

    @GET("liaison-notes/classes/{classId}/students")
    suspend fun getSiswa(
        @Path("classId") classId: Int
    ): ApiResponse<List<SiswaBukuPenghubungDto>>

    @POST("liaison-notes")
    suspend fun simpan(
        @Body request: CatatanBukuPenghubungRequest
    ): ApiResponse<CatatanBukuPenghubungDto>
}
