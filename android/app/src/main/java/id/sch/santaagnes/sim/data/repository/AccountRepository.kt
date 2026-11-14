package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.domain.model.AuthUser
import id.sch.santaagnes.sim.domain.model.Profile
import javax.inject.Inject
import javax.inject.Singleton

/** Profil dan ubah password akun yang sedang login (F-AUTH-03). */
interface AccountRepository {

    /** Profil dari sesi tersimpan (tanpa jaringan); null bila belum ada sesi. */
    suspend fun cachedProfile(): Profile?

    suspend fun refreshProfile(): Resource<Profile>

    suspend fun changePassword(currentPassword: String, newPassword: String): Resource<Unit>
}

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionManager
) : AccountRepository {

    override suspend fun cachedProfile(): Profile? {
        val role = session.getRole() ?: return null
        return Profile(
            name = session.getUserName().orEmpty(),
            email = null,
            role = role,
            identifier = null,
            homeroomClasses = session.getHomeroomClassGroups().map { it.name },
            children = session.getChildren().map { it.name }
        )
    }

    override suspend fun refreshProfile(): Resource<Profile> =
        when (val result = auth.getProfile()) {
            is Resource.Success -> Resource.Success(result.data.toProfile())
            is Resource.Error -> result
        }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Resource<Unit> =
        auth.changePassword(currentPassword, newPassword)

    private fun AuthUser.toProfile() = Profile(
        name = name,
        email = email,
        role = role,
        identifier = identifier,
        homeroomClasses = homeroomClassGroups.map { it.name },
        children = children.map { it.name }
    )
}
