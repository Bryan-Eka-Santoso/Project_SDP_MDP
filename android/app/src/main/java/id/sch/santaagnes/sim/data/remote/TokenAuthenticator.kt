
package id.sch.santaagnes.sim.data.remote

import id.sch.santaagnes.sim.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val baseUrl: String
) : Authenticator {

    private val lock = Any()

    private val refreshApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(AuthApi::class.java)
    }

    override fun authenticate(
        route: Route?,
        response: Response
    ): Request? {

        // Hindari percobaan refresh berulang.
        if (responseCount(response) >= 2) {
            return null
        }

        val path = response.request.url.encodedPath

        if (
            path.endsWith("/auth/login") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/logout")
        ) {
            return null
        }

        return synchronized(lock) {
            runBlocking {
                val currentAccessToken =
                    sessionManager.getAccessToken()

                val requestToken = response.request
                    .header("Authorization")
                    ?.removePrefix("Bearer ")

                // Jika request lain sudah refresh token,
                // gunakan token terbaru.
                if (
                    !currentAccessToken.isNullOrBlank() &&
                    requestToken != currentAccessToken
                ) {
                    return@runBlocking response.request
                        .newBuilder()
                        .header(
                            "Authorization",
                            "Bearer $currentAccessToken"
                        )
                        .build()
                }

                val refreshToken =
                    sessionManager.getRefreshToken()
                        ?: return@runBlocking null

                try {
                    val result = refreshApi.refresh(
                        RefreshRequest(refreshToken)
                    )

                    val tokens = result.data

                    if (
                        !result.success ||
                        tokens == null
                    ) {
                        sessionManager.clearSession()
                        return@runBlocking null
                    }

                    sessionManager.saveTokens(
                        tokens.accessToken,
                        tokens.refreshToken
                    )

                    response.request
                        .newBuilder()
                        .header(
                            "Authorization",
                            "Bearer ${tokens.accessToken}"
                        )
                        .build()

                } catch (exception: Exception) {
                    // Jangan hapus sesi jika hanya
                    // terjadi gangguan jaringan.
                    null
                }
            }
        }
    }

    private fun responseCount(
        response: Response
    ): Int {
        var count = 1
        var previous = response.priorResponse

        while (previous != null) {
            count++
            previous = previous.priorResponse
        }

        return count
    }
}
