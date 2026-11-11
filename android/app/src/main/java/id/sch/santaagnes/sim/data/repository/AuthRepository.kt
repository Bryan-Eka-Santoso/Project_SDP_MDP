
package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AuthApi
import id.sch.santaagnes.sim.data.remote.LoginRequest
import id.sch.santaagnes.sim.data.remote.LogoutRequest
import id.sch.santaagnes.sim.data.remote.UserDto
import id.sch.santaagnes.sim.domain.model.AuthUser
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class AuthRepository  @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {

    suspend fun login(
        email: String,
        password: String
    ): Result<AuthUser> = safeCall {

        val response = authApi.login(
            LoginRequest(email, password)
        )

        val data = response.data
            ?: error("Data login tidak tersedia.")

        if (!response.success) {
            error("Login gagal.")
        }

        sessionManager.saveTokens(
            data.accessToken,
            data.refreshToken
        )

        data.user.toDomain()
    }

    suspend fun getProfile(): Result<AuthUser> = safeCall {

        val response = authApi.getProfile()

        val user = response.data
            ?: error("Profil tidak tersedia.")

        user.toDomain()
    }

    suspend fun logout(): Result<Unit> = safeCall {

        val refreshToken =
            sessionManager.getRefreshToken()

        if (refreshToken != null) {
            authApi.logout(
                LogoutRequest(refreshToken)
            )
        }

        sessionManager.clearSession()
    }

    private suspend fun <T> safeCall(
        action: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(action())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            android.util.Log.e(
                "AUTH_DEBUG",
                "Gagal menghubungi backend",
                exception
            )
            Result.failure(
                Exception("Tidak dapat terhubung ke server.")
            )
        } catch (exception: HttpException) {
            val message = when (exception.code()) {
                400 -> "Data yang dikirim tidak valid."
                401 -> "Email, password, atau sesi tidak valid."
                403 -> "Anda tidak memiliki akses."
                404 -> "Data tidak ditemukan."
                else -> "Terjadi kesalahan server."
            }
            Result.failure(Exception(message))
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun UserDto.toDomain(): AuthUser {
        return AuthUser(
            id = id,
            name = name,
            email = email,
            role = role,
            isHomeroomTeacher =
                teacher?.isHomeroomTeacher ?: false
        )
    }
}
