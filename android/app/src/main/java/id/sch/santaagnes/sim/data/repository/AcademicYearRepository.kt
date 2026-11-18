package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.AcademicYearApi
import id.sch.santaagnes.sim.data.remote.CreateAcademicYearRequest
import id.sch.santaagnes.sim.data.remote.CreateSemesterRequest
import id.sch.santaagnes.sim.data.remote.UpdateAcademicYearRequest
import id.sch.santaagnes.sim.data.remote.UpdateSemesterRequest
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterAcademicYearDetail
import id.sch.santaagnes.sim.domain.model.MasterSemester
import javax.inject.Inject
import javax.inject.Singleton

/** Repository Tahun Ajaran dan Semester aktif/kelola (F-MST-06). */
interface AcademicYearRepository {
    suspend fun getActiveSemester(): Resource<MasterSemester>
    suspend fun getAcademicYears(page: Int = 1, limit: Int = 50, search: String? = null): Resource<List<MasterAcademicYear>>
    suspend fun getAcademicYearDetail(id: Int): Resource<MasterAcademicYearDetail>
    suspend fun createAcademicYear(name: String, startDate: String, endDate: String, isActive: Boolean): Resource<MasterAcademicYear>
    suspend fun updateAcademicYear(id: Int, name: String?, startDate: String?, endDate: String?): Resource<MasterAcademicYear>
    suspend fun setActiveAcademicYear(id: Int): Resource<MasterAcademicYear>
    suspend fun deleteAcademicYear(id: Int): Resource<Unit>

    suspend fun getSemesters(academicYearId: Int? = null): Resource<List<MasterSemester>>
    suspend fun createSemester(academicYearId: Int, type: String, startDate: String, endDate: String, isActive: Boolean): Resource<MasterSemester>
    suspend fun updateSemester(id: Int, startDate: String?, endDate: String?): Resource<MasterSemester>
    suspend fun setActiveSemester(id: Int): Resource<MasterSemester>
    suspend fun deleteSemester(id: Int): Resource<Unit>
}

@Singleton
class AcademicYearRepositoryImpl @Inject constructor(
    private val api: AcademicYearApi,
    private val errorMapper: ApiErrorMapper
) : AcademicYearRepository {

    override suspend fun getActiveSemester(): Resource<MasterSemester> = errorMapper.call {
        val dto = api.getActiveSemester().data ?: error("Semester aktif belum diatur.")
        dto.toDomain()
    }

    override suspend fun getAcademicYears(
        page: Int,
        limit: Int,
        search: String?
    ): Resource<List<MasterAcademicYear>> = errorMapper.call {
        val list = api.getAcademicYears(page, limit, search?.takeIf { it.isNotBlank() }).data
            ?: error("Gagal memuat tahun ajaran.")
        list.map { it.toDomain() }
    }

    override suspend fun getAcademicYearDetail(id: Int): Resource<MasterAcademicYearDetail> = errorMapper.call {
        val dto = api.getAcademicYearById(id).data ?: error("Detail tahun ajaran tidak ditemukan.")
        dto.toDetailDomain()
    }

    override suspend fun createAcademicYear(
        name: String,
        startDate: String,
        endDate: String,
        isActive: Boolean
    ): Resource<MasterAcademicYear> = errorMapper.call {
        val dto = api.createAcademicYear(CreateAcademicYearRequest(name, startDate, endDate, isActive)).data
            ?: error("Gagal menambah tahun ajaran.")
        dto.toDomain()
    }

    override suspend fun updateAcademicYear(
        id: Int,
        name: String?,
        startDate: String?,
        endDate: String?
    ): Resource<MasterAcademicYear> = errorMapper.call {
        val dto = api.updateAcademicYear(id, UpdateAcademicYearRequest(name, startDate, endDate)).data
            ?: error("Gagal mengubah tahun ajaran.")
        dto.toDomain()
    }

    override suspend fun setActiveAcademicYear(id: Int): Resource<MasterAcademicYear> = errorMapper.call {
        val dto = api.setActiveAcademicYear(id).data ?: error("Gagal mengaktifkan tahun ajaran.")
        dto.toDomain()
    }

    override suspend fun deleteAcademicYear(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(api.deleteAcademicYear(id))
    }

    override suspend fun getSemesters(academicYearId: Int?): Resource<List<MasterSemester>> = errorMapper.call {
        val list = api.getSemesters(academicYearId = academicYearId).data
            ?: error("Gagal memuat daftar semester.")
        list.map { it.toDomain() }
    }

    override suspend fun createSemester(
        academicYearId: Int,
        type: String,
        startDate: String,
        endDate: String,
        isActive: Boolean
    ): Resource<MasterSemester> = errorMapper.call {
        val dto = api.createSemester(CreateSemesterRequest(academicYearId, type, startDate, endDate, isActive)).data
            ?: error("Gagal menambah semester.")
        dto.toDomain()
    }

    override suspend fun updateSemester(
        id: Int,
        startDate: String?,
        endDate: String?
    ): Resource<MasterSemester> = errorMapper.call {
        val dto = api.updateSemester(id, UpdateSemesterRequest(startDate, endDate)).data
            ?: error("Gagal mengubah semester.")
        dto.toDomain()
    }

    override suspend fun setActiveSemester(id: Int): Resource<MasterSemester> = errorMapper.call {
        val dto = api.setActiveSemester(id).data ?: error("Gagal mengaktifkan semester.")
        dto.toDomain()
    }

    override suspend fun deleteSemester(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(api.deleteSemester(id))
    }
}
