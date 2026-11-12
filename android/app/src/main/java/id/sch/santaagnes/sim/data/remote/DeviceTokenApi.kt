package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

data class DeviceTokenRequest(
    val token: String,
    val platform: String = "ANDROID"
)

/** Pendaftaran token FCM perangkat ini ke akun yang sedang login (F-NTF-02). */
interface DeviceTokenApi {

    // Server memakai upsert: mendaftarkan token yang sama berulang kali aman.
    @POST("me/device-tokens")
    suspend fun register(@Body request: DeviceTokenRequest): ApiResponse<Unit>
}
