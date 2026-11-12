
package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class LogoutRequest(
    val refreshToken: String,
    val deviceToken: String? = null
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class TokenDto(
    val accessToken: String,
    val refreshToken: String
)

data class StudentDto(
    val id: Int,
    val nis: String?,
    val birthDate: String?
)

data class TeacherDto(
    val id: Int,
    val employeeNumber: String?,
    val isHomeroomTeacher: Boolean,
    // kelas binaan tahun ajaran aktif; null/kosong bila bukan wali kelas
    val homeroomClassGroups: List<IdNameDto>? = null
)

data class GuardianChildDto(
    val id: Int,
    val name: String,
    val nis: String?,
    val relationship: String?
)

data class GuardianDto(
    val id: Int,
    val phone: String?,
    val children: List<GuardianChildDto>
)

data class UserDto(
    val id: Int,
    val email: String,
    val name: String,
    val role: String,
    val student: StudentDto?,
    val teacher: TeacherDto?,
    val guardian: GuardianDto?
)

data class LoginDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto
)

interface AuthApi {

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): ApiResponse<LoginDto>

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): ApiResponse<TokenDto>

    @POST("auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): ApiResponse<Unit>

    @GET("me")
    suspend fun getProfile(): ApiResponse<UserDto>

    @PATCH("me/password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): ApiResponse<TokenDto>
}
