
package id.sch.santaagnes.sim.data.remote

import com.google.gson.JsonObject

data class LoginRequest(
    val email: String,
    val password: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiErrorDto? = null
)

data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null
)

data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: JsonObject? = null
)
