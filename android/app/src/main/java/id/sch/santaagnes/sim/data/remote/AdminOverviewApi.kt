package id.sch.santaagnes.sim.data.remote

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Hitung jumlah data lewat `meta.total` daftar apa pun (limit 1, isinya tidak dipakai). Dipakai
 * ringkasan dashboard admin agar tidak perlu endpoint baru di backend.
 */
interface AdminOverviewApi {

    @GET
    suspend fun list(
        @Url path: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 1
    ): ApiResponse<List<JsonElement>>
}
