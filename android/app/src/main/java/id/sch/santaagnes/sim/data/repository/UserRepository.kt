package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateUserRequest
import id.sch.santaagnes.sim.data.remote.ResetUserPasswordRequest
import id.sch.santaagnes.sim.data.remote.UpdateRoleRequest
import id.sch.santaagnes.sim.data.remote.UpdateStatusRequest
import id.sch.santaagnes.sim.data.remote.UpdateUserRequest
import id.sch.santaagnes.sim.data.remote.UserApi
import id.sch.santaagnes.sim.domain.model.MasterUser
import id.sch.santaagnes.sim.domain.model.UserFilter
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun getUsers(
        page: Int = 1,
        limit: Int = 20,
        filter: UserFilter = UserFilter()
    ): Resource<Pair<List<MasterUser>, Int>>

    suspend fun createUser(
        email: String,
        password: String,
        name: String,
        role: String
    ): Resource<MasterUser>

    suspend fun updateUser(
        id: Int,
        name: String?,
        email: String?
    ): Resource<MasterUser>

    suspend fun updateRole(
        id: Int,
        role: String
    ): Resource<MasterUser>

    suspend fun updateStatus(
        id: Int,
        status: String
    ): Resource<MasterUser>

    suspend fun resetPassword(
        id: Int,
        newPassword: String
    ): Resource<Unit>

    suspend fun deleteUser(
        id: Int
    ): Resource<Unit>
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val errorMapper: ApiErrorMapper
) : UserRepository {

    override suspend fun getUsers(
        page: Int,
        limit: Int,
        filter: UserFilter
    ): Resource<Pair<List<MasterUser>, Int>> = errorMapper.call {
        val response = userApi.getUsers(
            page = page,
            limit = limit,
            search = filter.search?.takeIf { it.isNotBlank() },
            role = filter.role?.takeIf { it.isNotBlank() && it != "ALL" },
            status = filter.status?.takeIf { it.isNotBlank() && it != "ALL" }
        )
        val data = response.data ?: error("Gagal memuat data pengguna")
        val items = data.map {
            MasterUser(
                id = it.id,
                email = it.email,
                name = it.name,
                role = it.role,
                status = it.status,
                createdAt = it.createdAt
            )
        }
        val total = response.meta?.total ?: items.size
        Pair(items, total)
    }

    override suspend fun createUser(
        email: String,
        password: String,
        name: String,
        role: String
    ): Resource<MasterUser> = errorMapper.call {
        val response = userApi.createUser(
            CreateUserRequest(email, password, name, role)
        )
        val it = response.data ?: error("Gagal membuat pengguna")
        MasterUser(
            id = it.id,
            email = it.email,
            name = it.name,
            role = it.role,
            status = it.status,
            createdAt = it.createdAt
        )
    }

    override suspend fun updateUser(
        id: Int,
        name: String?,
        email: String?
    ): Resource<MasterUser> = errorMapper.call {
        val response = userApi.updateUser(
            id = id,
            body = UpdateUserRequest(name = name, email = email)
        )
        val it = response.data ?: error("Gagal mengubah pengguna")
        MasterUser(
            id = it.id,
            email = it.email,
            name = it.name,
            role = it.role,
            status = it.status,
            createdAt = it.createdAt
        )
    }

    override suspend fun updateRole(
        id: Int,
        role: String
    ): Resource<MasterUser> = errorMapper.call {
        val response = userApi.updateRole(
            id = id,
            body = UpdateRoleRequest(role = role)
        )
        val it = response.data ?: error("Gagal memperbarui role")
        MasterUser(
            id = it.id,
            email = it.email,
            name = it.name,
            role = it.role,
            status = it.status,
            createdAt = it.createdAt
        )
    }

    override suspend fun updateStatus(
        id: Int,
        status: String
    ): Resource<MasterUser> = errorMapper.call {
        val response = userApi.updateStatus(
            id = id,
            body = UpdateStatusRequest(status = status)
        )
        val it = response.data ?: error("Gagal memperbarui status")
        MasterUser(
            id = it.id,
            email = it.email,
            name = it.name,
            role = it.role,
            status = it.status,
            createdAt = it.createdAt
        )
    }

    override suspend fun resetPassword(
        id: Int,
        newPassword: String
    ): Resource<Unit> = errorMapper.call {
        userApi.resetPassword(
            id = id,
            body = ResetUserPasswordRequest(newPassword = newPassword)
        )
        Unit
    }

    override suspend fun deleteUser(
        id: Int
    ): Resource<Unit> = errorMapper.call {
        val response = userApi.deleteUser(id = id)
        if (!response.isSuccessful && response.code() != 404) {
            throw HttpException(response)
        }
    }
}
