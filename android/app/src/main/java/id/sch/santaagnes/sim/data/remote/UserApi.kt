package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class UserItemDto(
    val id: Int,
    val email: String,
    val name: String,
    val role: String,
    val status: String,
    val createdAt: String?,
    val updatedAt: String?
)

data class CreateUserRequest(
    val email: String,
    val password: String,
    val name: String,
    val role: String
)

data class UpdateUserRequest(
    val name: String? = null,
    val email: String? = null
)

data class UpdateRoleRequest(
    val role: String
)

data class UpdateStatusRequest(
    val status: String
)

data class ResetUserPasswordRequest(
    val newPassword: String
)

interface UserApi {

    @GET("users")
    suspend fun getUsers(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("q") search: String? = null,
        @Query("role") role: String? = null,
        @Query("status") status: String? = null
    ): ApiResponse<List<UserItemDto>>

    @POST("users")
    suspend fun createUser(
        @Body body: CreateUserRequest
    ): ApiResponse<UserItemDto>

    @GET("users/{id}")
    suspend fun getUserById(
        @Path("id") id: Int
    ): ApiResponse<UserItemDto>

    @PATCH("users/{id}")
    suspend fun updateUser(
        @Path("id") id: Int,
        @Body body: UpdateUserRequest
    ): ApiResponse<UserItemDto>

    @PATCH("users/{id}/role")
    suspend fun updateRole(
        @Path("id") id: Int,
        @Body body: UpdateRoleRequest
    ): ApiResponse<UserItemDto>

    @PATCH("users/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: Int,
        @Body body: UpdateStatusRequest
    ): ApiResponse<UserItemDto>

    @POST("users/{id}/reset-password")
    suspend fun resetPassword(
        @Path("id") id: Int,
        @Body body: ResetUserPasswordRequest
    ): ApiResponse<Unit>

    @DELETE("users/{id}")
    suspend fun deleteUser(
        @Path("id") id: Int
    ): Response<Unit>
}
