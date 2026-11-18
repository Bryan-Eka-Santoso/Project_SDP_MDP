package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateGuardianRequest
import id.sch.santaagnes.sim.data.remote.GuardianApi
import id.sch.santaagnes.sim.data.remote.UpdateGuardianRequest
import id.sch.santaagnes.sim.domain.model.MasterGuardian
import javax.inject.Inject
import javax.inject.Singleton

/** Repository Wali Murid untuk manajemen relasi siswa <-> wali murid (F-MST-04). */
interface GuardianRepository {
    suspend fun getGuardians(search: String? = null): Resource<List<MasterGuardian>>
    suspend fun createGuardian(name: String, email: String, password: String, phone: String): Resource<MasterGuardian>
    suspend fun updateGuardian(id: Int, name: String?, email: String?, phone: String?): Resource<MasterGuardian>
    suspend fun deleteGuardian(id: Int): Resource<Unit>
}

@Singleton
class GuardianRepositoryImpl @Inject constructor(
    private val api: GuardianApi,
    private val errorMapper: ApiErrorMapper
) : GuardianRepository {

    override suspend fun getGuardians(search: String?): Resource<List<MasterGuardian>> = errorMapper.call {
        val list = api.getGuardians(page = 1, limit = 100, search = search?.takeIf { it.isNotBlank() }).data
            ?: error("Gagal memuat data wali murid.")
        list.map { it.toDomain() }
    }

    override suspend fun createGuardian(
        name: String,
        email: String,
        password: String,
        phone: String
    ): Resource<MasterGuardian> = errorMapper.call {
        val dto = api.createGuardian(CreateGuardianRequest(name, email, password, phone)).data
            ?: error("Gagal membuat akun wali murid.")
        dto.toDomain()
    }

    override suspend fun updateGuardian(
        id: Int,
        name: String?,
        email: String?,
        phone: String?
    ): Resource<MasterGuardian> = errorMapper.call {
        val dto = api.updateGuardian(id, UpdateGuardianRequest(name, email, phone)).data
            ?: error("Gagal mengubah data wali murid.")
        dto.toDomain()
    }

    override suspend fun deleteGuardian(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(api.deleteGuardian(id))
    }
}
