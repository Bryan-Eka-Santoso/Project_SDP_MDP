package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.*

data class FasilitasDto(
    val id: Int,
    val name: String,
    val type: String,
    val quantity: Int
)

data class SimpanFasilitasRequest(
    val name: String,
    val type: String,
    val quantity: Int
)

interface FasilitasApi {

    @GET("facilities")
    suspend fun getAll(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("type") type: String? = null
    ): ApiResponse<List<FasilitasDto>>

    @POST("facilities")
    suspend fun tambah(
        @Body body: SimpanFasilitasRequest
    ): ApiResponse<FasilitasDto>

    @PATCH("facilities/{id}")
    suspend fun edit(
        @Path("id") id: Int,
        @Body body: SimpanFasilitasRequest
    ): ApiResponse<FasilitasDto>

    @DELETE("facilities/{id}")
    suspend fun hapus(
        @Path("id") id: Int
    ): Response<Unit>
}