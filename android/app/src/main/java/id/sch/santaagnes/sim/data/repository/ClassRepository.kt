package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.AddClassMembersRequest
import id.sch.santaagnes.sim.data.remote.ClassApi
import id.sch.santaagnes.sim.data.remote.CreateClassRequest
import id.sch.santaagnes.sim.data.remote.MoveClassMemberRequest
import id.sch.santaagnes.sim.data.remote.SetHomeroomTeacherRequest
import id.sch.santaagnes.sim.data.remote.UpdateClassRequest
import id.sch.santaagnes.sim.domain.model.ClassFilter
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterClassMembersPage
import javax.inject.Inject
import javax.inject.Singleton

/** Repository Master Kelas (F-MST-07), Wali Kelas (F-MST-08), dan Anggota Kelas (F-MST-09). */
interface ClassRepository {
    suspend fun getClasses(
        page: Int = 1,
        limit: Int = 100,
        filter: ClassFilter = ClassFilter()
    ): Resource<Pair<List<MasterClassGroup>, Int>>

    suspend fun createClass(
        academicYearId: Int,
        name: String,
        gradeLevel: String,
        capacity: Int,
        homeroomTeacherId: Int?
    ): Resource<MasterClassGroup>

    suspend fun updateClass(
        id: Int,
        name: String?,
        gradeLevel: String?,
        capacity: Int?
    ): Resource<MasterClassGroup>

    suspend fun setHomeroomTeacher(
        classId: Int,
        homeroomTeacherId: Int?
    ): Resource<MasterClassGroup>

    suspend fun deleteClass(id: Int): Resource<Unit>

    suspend fun getClassMembers(classId: Int): Resource<MasterClassMembersPage>
    suspend fun addClassMembers(classId: Int, studentIds: List<Int>): Resource<MasterClassMembersPage>
    suspend fun moveClassMember(classId: Int, studentId: Int, targetClassGroupId: Int): Resource<Unit>
    suspend fun removeClassMember(classId: Int, studentId: Int): Resource<Unit>
}

@Singleton
class ClassRepositoryImpl @Inject constructor(
    private val api: ClassApi,
    private val errorMapper: ApiErrorMapper
) : ClassRepository {

    override suspend fun getClasses(
        page: Int,
        limit: Int,
        filter: ClassFilter
    ): Resource<Pair<List<MasterClassGroup>, Int>> = errorMapper.call {
        val response = api.getClasses(
            page = page,
            limit = limit,
            academicYearId = filter.academicYearId,
            gradeLevel = filter.gradeLevel?.takeIf { it.isNotBlank() },
            search = filter.search?.takeIf { it.isNotBlank() }
        )
        val items = (response.data ?: error("Gagal memuat data kelas.")).map { it.toDomain() }
        Pair(items, response.meta?.total ?: items.size)
    }

    override suspend fun createClass(
        academicYearId: Int,
        name: String,
        gradeLevel: String,
        capacity: Int,
        homeroomTeacherId: Int?
    ): Resource<MasterClassGroup> = errorMapper.call {
        val dto = api.createClass(
            CreateClassRequest(academicYearId, name, gradeLevel, capacity, homeroomTeacherId)
        ).data ?: error("Gagal menambah kelas.")
        dto.toDomain()
    }

    override suspend fun updateClass(
        id: Int,
        name: String?,
        gradeLevel: String?,
        capacity: Int?
    ): Resource<MasterClassGroup> = errorMapper.call {
        val dto = api.updateClass(
            id,
            UpdateClassRequest(name, gradeLevel, capacity)
        ).data ?: error("Gagal memperbarui data kelas.")
        dto.toDomain()
    }

    override suspend fun setHomeroomTeacher(
        classId: Int,
        homeroomTeacherId: Int?
    ): Resource<MasterClassGroup> = errorMapper.call {
        val dto = api.setHomeroomTeacher(
            classId,
            SetHomeroomTeacherRequest(homeroomTeacherId)
        ).data ?: error("Gagal mengatur wali kelas.")
        dto.toDomain()
    }

    override suspend fun deleteClass(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(api.deleteClass(id))
    }

    override suspend fun getClassMembers(classId: Int): Resource<MasterClassMembersPage> = errorMapper.call {
        val dto = api.getClassMembers(classId).data ?: error("Gagal memuat anggota kelas.")
        dto.toDomain()
    }

    override suspend fun addClassMembers(
        classId: Int,
        studentIds: List<Int>
    ): Resource<MasterClassMembersPage> = errorMapper.call {
        val dto = api.addClassMembers(classId, AddClassMembersRequest(studentIds)).data
            ?: error("Gagal menambahkan siswa ke kelas.")
        dto.toDomain()
    }

    override suspend fun moveClassMember(
        classId: Int,
        studentId: Int,
        targetClassGroupId: Int
    ): Resource<Unit> = errorMapper.call {
        api.moveClassMember(classId, MoveClassMemberRequest(studentId, targetClassGroupId))
        Unit
    }

    override suspend fun removeClassMember(classId: Int, studentId: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(api.removeClassMember(classId, studentId))
    }
}
