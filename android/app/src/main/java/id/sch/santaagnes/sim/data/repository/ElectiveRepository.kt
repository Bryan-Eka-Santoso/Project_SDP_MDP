package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateSubjectPackageRequest
import id.sch.santaagnes.sim.data.remote.ElectiveApi
import id.sch.santaagnes.sim.data.remote.MataPelajaranApi
import id.sch.santaagnes.sim.data.remote.UpdateSubjectPackageRequest
import id.sch.santaagnes.sim.domain.model.PackageSubject
import id.sch.santaagnes.sim.domain.model.SubjectPackage
import javax.inject.Inject
import javax.inject.Singleton

/** Repository master paket mata pelajaran pilihan (F-MPP-03). */
interface ElectiveRepository {
    suspend fun getSubjectPackages(
        academicYearId: Int? = null,
        search: String? = null
    ): Resource<List<SubjectPackage>>

    suspend fun createSubjectPackage(
        academicYearId: Int,
        name: String,
        capacity: Int,
        subjectIds: List<Int>
    ): Resource<SubjectPackage>

    suspend fun updateSubjectPackage(
        id: Int,
        name: String? = null,
        capacity: Int? = null,
        subjectIds: List<Int>? = null
    ): Resource<SubjectPackage>

    suspend fun deleteSubjectPackage(id: Int): Resource<Unit>

    suspend fun getSubjects(): Resource<List<PackageSubject>>
}

@Singleton
class ElectiveRepositoryImpl @Inject constructor(
    private val electiveApi: ElectiveApi,
    private val subjectApi: MataPelajaranApi,
    private val errorMapper: ApiErrorMapper
) : ElectiveRepository {

    override suspend fun getSubjectPackages(
        academicYearId: Int?,
        search: String?
    ): Resource<List<SubjectPackage>> = errorMapper.call {
        val list = electiveApi.getSubjectPackages(
            page = 1,
            limit = 100,
            academicYearId = academicYearId,
            search = search?.takeIf { it.isNotBlank() }
        ).data ?: error("Gagal memuat paket mata pelajaran pilihan.")
        list.map { it.toDomain() }
    }

    override suspend fun createSubjectPackage(
        academicYearId: Int,
        name: String,
        capacity: Int,
        subjectIds: List<Int>
    ): Resource<SubjectPackage> = errorMapper.call {
        val dto = electiveApi.createSubjectPackage(
            CreateSubjectPackageRequest(academicYearId, name, capacity, subjectIds)
        ).data ?: error("Gagal membuat paket mata pelajaran.")
        dto.toDomain()
    }

    override suspend fun updateSubjectPackage(
        id: Int,
        name: String?,
        capacity: Int?,
        subjectIds: List<Int>?
    ): Resource<SubjectPackage> = errorMapper.call {
        val dto = electiveApi.updateSubjectPackage(
            id,
            UpdateSubjectPackageRequest(name, capacity, subjectIds)
        ).data ?: error("Gagal memperbarui paket mata pelajaran.")
        dto.toDomain()
    }

    override suspend fun deleteSubjectPackage(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(electiveApi.deleteSubjectPackage(id))
    }

    override suspend fun getSubjects(): Resource<List<PackageSubject>> = errorMapper.call {
        val list = subjectApi.getAll(page = 1, limit = 100).data ?: emptyList()
        list.map { PackageSubject(id = it.id, code = it.code, name = it.name, type = it.type) }
    }
}
