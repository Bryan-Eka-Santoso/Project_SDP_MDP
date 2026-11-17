package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class FasilitasPeminjamanDto(
    val id: Int,
    val name: String,
    val type: String,
    val quantity: Int
)

data class AjukanPeminjamanRequest(
    val facilityId: Int,
    val bookingDate: String,
    val startTime: String,
    val endTime: String,
    val purpose: String
)

data class HasilPengajuanDto(
    val id: Int,
    val facilityId: Int,
    val facilityName: String,
    val facilityType: String,
    val bookingDate: String,
    val startTime: String,
    val endTime: String,
    val purpose: String,
    val status: String
)

interface AjukanPeminjamanApi {

    @GET("booking-requests/facilities")
    suspend fun getFasilitas():
            ApiResponse<List<FasilitasPeminjamanDto>>

    @POST("booking-requests")
    suspend fun ajukan(
        @Body request: AjukanPeminjamanRequest
    ): ApiResponse<HasilPengajuanDto>
}