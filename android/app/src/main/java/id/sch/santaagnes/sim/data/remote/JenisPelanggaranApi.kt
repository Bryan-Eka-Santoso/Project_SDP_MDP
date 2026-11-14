package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.*

data class JenisPelanggaranDto(
    val id: Int,
    val name: String,
    val points: Int
)

data class SimpanJenisPelanggaranRequest(
    val name: String,
    val points: Int
)

interface JenisPelanggaranApi {

    @GET("violation-types")
    suspend fun getAll(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("q") search: String? = null
    ): ApiResponse<List<JenisPelanggaranDto>>

    @POST("violation-types")
    suspend fun tambah(
        @Body body: SimpanJenisPelanggaranRequest
    ): ApiResponse<JenisPelanggaranDto>

    @PATCH("violation-types/{id}")
    suspend fun edit(
        @Path("id") id: Int,
        @Body body: SimpanJenisPelanggaranRequest
    ): ApiResponse<JenisPelanggaranDto>

    @DELETE("violation-types/{id}")
    suspend fun hapus(
        @Path("id") id: Int
    ): Response<Unit>
}