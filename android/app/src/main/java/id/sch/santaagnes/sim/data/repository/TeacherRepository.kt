package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateTeacherRequest
import id.sch.santaagnes.sim.data.remote.CreateTeachingAssignmentRequest
import id.sch.santaagnes.sim.data.remote.MataPelajaranApi
import id.sch.santaagnes.sim.data.remote.MataPelajaranDto
import id.sch.santaagnes.sim.data.remote.TeacherApi
import id.sch.santaagnes.sim.data.remote.TeachingAssignmentApi
import id.sch.santaagnes.sim.data.remote.UpdateTeacherRequest
import id.sch.santaagnes.sim.data.remote.UpdateTeachingAssignmentRequest
import id.sch.santaagnes.sim.domain.model.MasterTeacher
import id.sch.santaagnes.sim.domain.model.MasterTeacherDetail
import id.sch.santaagnes.sim.domain.model.MasterTeachingAssignment
import id.sch.santaagnes.sim.domain.model.TeacherFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository terpadu Guru (F-MST-05) & Penugasan Guru Pengajar (F-MST-10).
 * Menggunakan ulang MataPelajaranApi untuk daftar mata pelajaran.
 */
interface TeacherRepository {
    suspend fun getTeachers(
        page: Int = 1,
        limit: Int = 100,
        filter: TeacherFilter = TeacherFilter()
    ): Resource<Pair<List<MasterTeacher>, Int>>

    suspend fun getTeacherDetail(id: Int): Resource<MasterTeacherDetail>

    suspend fun createTeacher(
        name: String,
        email: String,
        password: String,
        employeeNumber: String
    ): Resource<MasterTeacher>

    suspend fun updateTeacher(
        id: Int,
        name: String?,
        email: String?,
        employeeNumber: String?
    ): Resource<MasterTeacher>

    suspend fun deleteTeacher(id: Int): Resource<Unit>

    suspend fun getTeachingAssignments(
        semesterId: Int? = null,
        teacherId: Int? = null,
        classGroupId: Int? = null
    ): Resource<Pair<List<MasterTeachingAssignment>, Int>>

    suspend fun createTeachingAssignment(
        teacherId: Int,
        subjectId: Int,
        classGroupId: Int,
        semesterId: Int
    ): Resource<MasterTeachingAssignment>

    suspend fun updateTeachingAssignmentTeacher(
        assignmentId: Int,
        teacherId: Int
    ): Resource<MasterTeachingAssignment>

    suspend fun deleteTeachingAssignment(assignmentId: Int): Resource<Unit>

    suspend fun getSubjects(): Resource<List<MataPelajaranDto>>
}

@Singleton
class TeacherRepositoryImpl @Inject constructor(
    private val teacherApi: TeacherApi,
    private val assignmentApi: TeachingAssignmentApi,
    private val subjectApi: MataPelajaranApi,
    private val errorMapper: ApiErrorMapper
) : TeacherRepository {

    override suspend fun getTeachers(
        page: Int,
        limit: Int,
        filter: TeacherFilter
    ): Resource<Pair<List<MasterTeacher>, Int>> = errorMapper.call {
        val response = teacherApi.getTeachers(
            page = page,
            limit = limit,
            search = filter.search?.takeIf { it.isNotBlank() },
            isHomeroomTeacher = filter.isHomeroomTeacher
        )
        val items = (response.data ?: error("Gagal memuat data guru.")).map { it.toDomain() }
        Pair(items, response.meta?.total ?: items.size)
    }

    override suspend fun getTeacherDetail(id: Int): Resource<MasterTeacherDetail> = errorMapper.call {
        val dto = teacherApi.getTeacherById(id).data ?: error("Detail guru tidak ditemukan.")
        dto.toDetailDomain()
    }

    override suspend fun createTeacher(
        name: String,
        email: String,
        password: String,
        employeeNumber: String
    ): Resource<MasterTeacher> = errorMapper.call {
        val dto = teacherApi.createTeacher(
            CreateTeacherRequest(name, email, password, employeeNumber)
        ).data ?: error("Gagal menambah data guru.")
        dto.toDomain()
    }

    override suspend fun updateTeacher(
        id: Int,
        name: String?,
        email: String?,
        employeeNumber: String?
    ): Resource<MasterTeacher> = errorMapper.call {
        val dto = teacherApi.updateTeacher(
            id,
            UpdateTeacherRequest(name, email, employeeNumber)
        ).data ?: error("Gagal memperbarui data guru.")
        dto.toDomain()
    }

    override suspend fun deleteTeacher(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(teacherApi.deleteTeacher(id))
    }

    override suspend fun getTeachingAssignments(
        semesterId: Int?,
        teacherId: Int?,
        classGroupId: Int?
    ): Resource<Pair<List<MasterTeachingAssignment>, Int>> = errorMapper.call {
        val response = assignmentApi.getTeachingAssignments(
            page = 1,
            limit = 100,
            semesterId = semesterId,
            teacherId = teacherId,
            classGroupId = classGroupId
        )
        val items = (response.data ?: error("Gagal memuat penugasan mengajar.")).map { it.toDomain() }
        Pair(items, response.meta?.total ?: items.size)
    }

    override suspend fun createTeachingAssignment(
        teacherId: Int,
        subjectId: Int,
        classGroupId: Int,
        semesterId: Int
    ): Resource<MasterTeachingAssignment> = errorMapper.call {
        val dto = assignmentApi.createTeachingAssignment(
            CreateTeachingAssignmentRequest(teacherId, subjectId, classGroupId, semesterId)
        ).data ?: error("Gagal menambah penugasan guru.")
        dto.toDomain()
    }

    override suspend fun updateTeachingAssignmentTeacher(
        assignmentId: Int,
        teacherId: Int
    ): Resource<MasterTeachingAssignment> = errorMapper.call {
        val dto = assignmentApi.updateTeachingAssignment(
            assignmentId,
            UpdateTeachingAssignmentRequest(teacherId)
        ).data ?: error("Gagal memperbarui guru pengajar.")
        dto.toDomain()
    }

    override suspend fun deleteTeachingAssignment(assignmentId: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(assignmentApi.deleteTeachingAssignment(assignmentId))
    }

    override suspend fun getSubjects(): Resource<List<MataPelajaranDto>> = errorMapper.call {
        subjectApi.getAll(page = 1, limit = 100).data ?: emptyList()
    }
}
