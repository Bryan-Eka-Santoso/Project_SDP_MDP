package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.AppDatabase
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AuthApi
import id.sch.santaagnes.sim.data.remote.ChangePasswordRequest
import id.sch.santaagnes.sim.data.remote.LoginRequest
import id.sch.santaagnes.sim.data.remote.LogoutRequest
import id.sch.santaagnes.sim.data.remote.UserDto
import id.sch.santaagnes.sim.domain.model.AuthUser
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.notification.PushTokenProvider
import id.sch.santaagnes.sim.notification.PushTokenRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val database: AppDatabase,
    private val errors: ApiErrorMapper,
    private val pushTokens: PushTokenRegistrar,
    private val tokenProvider: PushTokenProvider
) {

    suspend fun login(
        email: String,
        password: String
    ): Resource<AuthUser> = errors.call {
        val response = authApi.login(LoginRequest(email, password))
        val data = checkNotNull(response.data) { "Data login tidak tersedia." }
        check(response.success) { "Login gagal." }

        sessionManager.saveTokens(data.accessToken, data.refreshToken)
        data.user.toDomain().also {
            saveUser(it)
            // Gagal mendaftarkan token tidak menggagalkan login; dicoba lagi saat aplikasi dibuka.
            withTimeoutOrNull(TOKEN_TIMEOUT_MS) { pushTokens.register() }
        }
    }

    suspend fun getProfile(): Resource<AuthUser> = errors.call {
        val user = checkNotNull(authApi.getProfile().data) { "Profil tidak tersedia." }
        user.toDomain().also { saveUser(it) }
    }

    /** Sesi di perangkat lain dicabut server; token baru untuk perangkat ini langsung disimpan. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Resource<Unit> = errors.call {
        val data = checkNotNull(authApi.changePassword(ChangePasswordRequest(currentPassword, newPassword)).data) {
            "Password gagal diubah."
        }
        sessionManager.saveTokens(data.accessToken, data.refreshToken)
    }

    /**
     * Keluar selalu menghapus sesi dan data lokal, meskipun server tidak terjangkau; kegagalan
     * mencabut token di server tidak boleh membuat pengguna terjebak di aplikasi.
     */
    suspend fun logout(): Resource<Unit> {
        val refreshToken = sessionManager.getRefreshToken()
        if (refreshToken != null) {
            // Server ikut menghapus token perangkat ini supaya akun lama tidak lagi menerima push.
            val deviceToken = withTimeoutOrNull(TOKEN_TIMEOUT_MS) { tokenProvider.currentToken() }
            errors.call { authApi.logout(LogoutRequest(refreshToken, deviceToken)) }
        }
        withTimeoutOrNull(TOKEN_TIMEOUT_MS) { tokenProvider.deleteToken() }
        sessionManager.clearSession()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        return Resource.Success(Unit)
    }

    private suspend fun saveUser(user: AuthUser) {
        sessionManager.saveUser(
            userId = user.id,
            name = user.name,
            role = user.role,
            isHomeroomTeacher = user.isHomeroomTeacher,
            studentId = user.studentId,
            homeroomClassGroups = user.homeroomClassGroups,
            children = user.children
        )
    }

    private companion object {
        // Firebase bisa lambat atau macet tanpa jaringan; login dan keluar tidak boleh menunggu lama.
        const val TOKEN_TIMEOUT_MS = 5_000L
    }

    private fun UserDto.toDomain(): AuthUser = AuthUser(
        id = id,
        name = name,
        email = email,
        role = role,
        identifier = student?.nis ?: teacher?.employeeNumber,
        isHomeroomTeacher = teacher?.isHomeroomTeacher ?: false,
        studentId = student?.id,
        homeroomClassGroups = teacher?.homeroomClassGroups.orEmpty().map { NamedId(it.id, it.name) },
        children = guardian?.children.orEmpty().map { NamedId(it.id, it.name) }
    )
}
