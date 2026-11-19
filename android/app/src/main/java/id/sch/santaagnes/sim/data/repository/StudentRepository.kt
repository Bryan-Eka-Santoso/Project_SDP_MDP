package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.core.TemplateFiles
import id.sch.santaagnes.sim.core.UploadFiles
import id.sch.santaagnes.sim.data.remote.CreateStudentRequest
import id.sch.santaagnes.sim.data.remote.LinkGuardianRequest
import id.sch.santaagnes.sim.data.remote.StudentApi
import id.sch.santaagnes.sim.data.remote.UpdateStudentRequest
import id.sch.santaagnes.sim.domain.model.MasterImportResult
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.domain.model.MasterStudentDetail
import id.sch.santaagnes.sim.domain.model.StudentFilter
import javax.inject.Inject
import javax.inject.Singleton

interface StudentRepository {
    suspend fun getStudents(
        page: Int = 1,
        limit: Int = 50,
        filter: StudentFilter = StudentFilter()
    ): Resource<Pair<List<MasterStudent>, Int>>

    suspend fun getStudentDetail(id: Int): Resource<MasterStudentDetail>

    suspend fun createStudent(
        request: CreateStudentRequest
    ): Resource<MasterStudent>

    suspend fun updateStudent(
        id: Int,
        request: UpdateStudentRequest
    ): Resource<MasterStudent>

    suspend fun deleteStudent(
        id: Int
    ): Resource<Unit>

    suspend fun linkGuardian(
        studentId: Int,
        guardianId: Int,
        relationship: String
    ): Resource<Unit>

    suspend fun unlinkGuardian(
        studentId: Int,
        guardianId: Int
    ): Resource<Unit>

    suspend fun fileName(uri: String): String?

    suspend fun saveImportTemplate(destination: String): Boolean

    suspend fun importStudents(uri: String): Resource<MasterImportResult>
}

@Singleton
class StudentRepositoryImpl @Inject constructor(
    private val studentApi: StudentApi,
    private val uploads: UploadFiles,
    private val templates: TemplateFiles,
    private val errorMapper: ApiErrorMapper
) : StudentRepository {

    override suspend fun getStudents(
        page: Int,
        limit: Int,
        filter: StudentFilter
    ): Resource<Pair<List<MasterStudent>, Int>> = errorMapper.call {
        val response = studentApi.getStudents(
            page = page,
            limit = limit,
            search = filter.search?.takeIf { it.isNotBlank() },
            classGroupId = filter.classGroupId
        )
        val data = response.data ?: error("Gagal memuat data siswa")
        val items = data.map { it.toDomain() }
        val total = response.meta?.total ?: items.size
        Pair(items, total)
    }

    override suspend fun getStudentDetail(id: Int): Resource<MasterStudentDetail> = errorMapper.call {
        val dto = studentApi.getStudentById(id).data ?: error("Detail siswa tidak ditemukan")
        dto.toDetailDomain()
    }

    override suspend fun createStudent(
        request: CreateStudentRequest
    ): Resource<MasterStudent> = errorMapper.call {
        val response = studentApi.createStudent(request)
        val dto = response.data ?: error("Gagal menambah siswa")
        dto.toDomain()
    }

    override suspend fun updateStudent(
        id: Int,
        request: UpdateStudentRequest
    ): Resource<MasterStudent> = errorMapper.call {
        val response = studentApi.updateStudent(id, request)
        val dto = response.data ?: error("Gagal memperbarui siswa")
        dto.toDomain()
    }

    override suspend fun deleteStudent(
        id: Int
    ): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(studentApi.deleteStudent(id), allowNotFound = true)
    }

    override suspend fun linkGuardian(
        studentId: Int,
        guardianId: Int,
        relationship: String
    ): Resource<Unit> = errorMapper.call {
        studentApi.linkGuardian(
            id = studentId,
            body = LinkGuardianRequest(guardianId, relationship)
        )
        Unit
    }

    override suspend fun unlinkGuardian(
        studentId: Int,
        guardianId: Int
    ): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(studentApi.unlinkGuardian(studentId, guardianId), allowNotFound = true)
    }

    override suspend fun fileName(uri: String): String? = uploads.name(uri)

    override suspend fun saveImportTemplate(destination: String): Boolean =
        templates.save("siswa.xlsx", destination)

    override suspend fun importStudents(uri: String): Resource<MasterImportResult> = errorMapper.call {
        val part = uploads.xlsxPart(uri, "siswa.xlsx")
        val dto = studentApi.importStudents(part).data ?: error("Gagal mengimpor data siswa.")
        dto.toDomain()
    }
}
