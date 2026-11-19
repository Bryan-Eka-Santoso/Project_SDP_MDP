package id.sch.santaagnes.sim.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface RiwayatPeminjamanApi {

    @GET("booking-history")
    suspend fun getRiwayat(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<PeminjamanDto>>
}