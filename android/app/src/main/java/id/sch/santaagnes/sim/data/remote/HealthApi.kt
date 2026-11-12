
package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.GET

interface HealthApi {

    @GET("api/v1/health")
    suspend fun checkHealth(): Response<HealthResponse>

}

data class HealthResponse(
    val status: String? = null
)
