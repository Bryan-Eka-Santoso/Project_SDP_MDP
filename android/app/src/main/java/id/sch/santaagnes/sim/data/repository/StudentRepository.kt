package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateStudentRequest
import id.sch.santaagnes.sim.data.remote.LinkGuardianRequest
import id.sch.santaagnes.sim.data.remote.StudentApi
import id.sch.santaagnes.sim.data.remote.UpdateStudentRequest
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.domain.model.StudentFilter
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

interface StudentRepository {
    suspend fun getStudents(
        page: Int = 1,
        limit: Int = 20,
        filter: StudentFilter = StudentFilter()
    ): Resource<Pair<List<MasterStudent>, Int>>

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
}

@Singleton
class StudentRepositoryImpl @Inject constructor(
    private val studentApi: StudentApi,
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
        val items = data.map {
            MasterStudent(
                id = it.id,
                userId = it.user.id,
                name = it.user.name,
                email = it.user.email,
                nis = it.nis,
                status = it.user.status,
                birthDate = it.birthDate,
                phone = it.phone,
                className = it.currentClass?.name,
                classId = it.currentClass?.id,
                guardianCount = it.guardians?.size ?: 0
            )
        }
        val total = response.meta?.total ?: items.size
        Pair(items, total)
    }

    override suspend fun createStudent(
        request: CreateStudentRequest
    ): Resource<MasterStudent> = errorMapper.call {
        val response = studentApi.createStudent(request)
        val it = response.data ?: error("Gagal menambah siswa")
        MasterStudent(
            id = it.id,
            userId = it.user.id,
            name = it.user.name,
            email = it.user.email,
            nis = it.nis,
            status = it.user.status,
            birthDate = it.birthDate,
            phone = it.phone,
            className = it.currentClass?.name,
            classId = it.currentClass?.id,
            guardianCount = it.guardians?.size ?: 0
        )
    }

    override suspend fun updateStudent(
        id: Int,
        request: UpdateStudentRequest
    ): Resource<MasterStudent> = errorMapper.call {
        val response = studentApi.updateStudent(id, request)
        val it = response.data ?: error("Gagal memperbarui siswa")
        MasterStudent(
            id = it.id,
            userId = it.user.id,
            name = it.user.name,
            email = it.user.email,
            nis = it.nis,
            status = it.user.status,
            birthDate = it.birthDate,
            phone = it.phone,
            className = it.currentClass?.name,
            classId = it.currentClass?.id,
            guardianCount = it.guardians?.size ?: 0
        )
    }

    override suspend fun deleteStudent(
        id: Int
    ): Resource<Unit> = errorMapper.call {
        val response = studentApi.deleteStudent(id)
        if (!response.isSuccessful && response.code() != 404) {
            throw HttpException(response)
        }
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
        val response = studentApi.unlinkGuardian(studentId, guardianId)
        if (!response.isSuccessful && response.code() != 404) {
            throw HttpException(response)
        }
    }
}
