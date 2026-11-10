
package id.sch.santaagnes.sim.data.remote

import id.sch.santaagnes.sim.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager
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

        return chain.proceed(authenticatedRequest)
    }
}
