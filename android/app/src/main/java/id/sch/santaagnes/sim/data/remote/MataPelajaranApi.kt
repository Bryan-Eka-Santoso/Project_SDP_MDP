package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.*

data class MataPelajaranDto(
    val id: Int,
    val code: String,
    val name: String,
    val type: String
)

data class SimpanMataPelajaranRequest(
    val code: String,
    val name: String,
    val type: String
)

interface MataPelajaranApi {

    @GET("subjects")
    suspend fun getAll(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): ApiResponse<List<MataPelajaranDto>>

    @POST("subjects")
    suspend fun tambah(
        @Body body: SimpanMataPelajaranRequest
    ): ApiResponse<MataPelajaranDto>

    @PATCH("subjects/{id}")
    suspend fun edit(
        @Path("id") id: Int,
        @Body body: SimpanMataPelajaranRequest
    ): ApiResponse<MataPelajaranDto>

    @DELETE("subjects/{id}")
    suspend fun hapus(
        @Path("id") id: Int
    ): Response<Unit>
}