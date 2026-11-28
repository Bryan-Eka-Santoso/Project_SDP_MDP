
package id.sch.santaagnes.sim.data.remote

import id.sch.santaagnes.sim.core.SessionEvents
import id.sch.santaagnes.sim.core.parseApiError
import id.sch.santaagnes.sim.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Admin memblokir akun saat pengguna sedang login. `peekBody` menjaga badan respons tetap terbaca. */
fun isAccountBlocked(response: Response): Boolean =
    response.code == 403 &&
        parseApiError(response.peekBody(ERROR_PEEK_BYTES).string())?.code == "ACCOUNT_BLOCKED"

private const val ERROR_PEEK_BYTES = 4_096L

class AuthInterceptor(
    private val sessionManager: SessionManager,
    private val sessionEvents: SessionEvents
) : Interceptor {

    override fun intercept(
        chain: Interceptor.Chain
    ): Response {

        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Endpoint auth tidak membutuhkan access token.
        if (
            path.endsWith("/auth/login") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/logout")
        ) {
            return chain.proceed(originalRequest)
        }

        val accessToken = runBlocking {
            sessionManager.getAccessToken()
        }

        if (accessToken.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest
            .newBuilder()
            .header(
                "Authorization",
                "Bearer $accessToken"
            )
            .build()

        val response = chain.proceed(authenticatedRequest)
        if (isAccountBlocked(response)) {
            runBlocking { sessionManager.clearSession() }
            sessionEvents.notifyExpired()
        }
        return response
    }
}
