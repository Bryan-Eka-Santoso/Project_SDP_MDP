
package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AuthApi
import id.sch.santaagnes.sim.data.remote.LoginRequest
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {

    suspend fun login(
        email: String,
        password: String
    ): Result<Unit> {
        return try {
            val response = authApi.login(
                LoginRequest(
                    email = email,
                    password = password
                )
            )

            val body = response.body()

            if (response.isSuccessful &&
                body?.success == true &&
                body.data != null
            ) {
                val accessToken = body.data.accessToken
                val refreshToken = body.data.refreshToken

                if (accessToken.isBlank() ||
                    refreshToken.isBlank()
                ) {
                    return Result.failure(
                        Exception("Token login tidak valid")
                    )
                }

                sessionManager.saveTokens(
                    accessToken,
                    refreshToken
                )

                Result.success(Unit)

            } else {
                val message = when (response.code()) {
                    400 -> "Format data login tidak valid"
                    401 -> "Email atau password salah"
                    403 -> "Akun tidak memiliki izin untuk masuk"
                    500 -> "Terjadi kesalahan pada server"
                    else -> "Login gagal (${response.code()})"
                }

                Result.failure(Exception(message))
            }

        } catch (e: CancellationException) {
            throw e

        } catch (e: IOException) {
            Result.failure(
                Exception(
                    "Tidak dapat terhubung ke server. Periksa koneksi.",
                    e
                )
            )

        } catch (e: HttpException) {
            Result.failure(
                Exception("Terjadi kesalahan HTTP ${e.code()}", e)
            )

        } catch (e: Exception) {
            Result.failure(
                Exception("Login gagal. Silakan coba lagi.", e)
            )
        }
    }
}
