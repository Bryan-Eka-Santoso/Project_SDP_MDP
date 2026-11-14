package id.sch.santaagnes.sim.data.remote

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class PeminjamanDto(
    val id: Int,
    val facilityId: Int,
    val facilityName: String,
    val facilityType: String,
    val requesterId: Int,
    val requesterName: String,
    val bookingDate: String,
    val startTime: String,
    val endTime: String,
    val purpose: String,
    val status: String,
    val verifierName: String?
)

interface PeminjamanApi {

    @GET("facility-bookings")
    suspend fun getAll(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("status") status: String? = null
    ): ApiResponse<List<PeminjamanDto>>

    @GET("facility-bookings/{id}")
    suspend fun getDetail(
        @Path("id") id: Int
    ): ApiResponse<PeminjamanDto>

    @POST("facility-bookings/{id}/approve")
    suspend fun setujui(
        @Path("id") id: Int
    ): ApiResponse<PeminjamanDto>

    @POST("facility-bookings/{id}/reject")
    suspend fun tolak(
        @Path("id") id: Int
    ): ApiResponse<PeminjamanDto>
}