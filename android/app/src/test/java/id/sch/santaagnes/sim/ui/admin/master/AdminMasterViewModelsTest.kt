package id.sch.santaagnes.sim.ui.admin.master

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateStudentRequest
import id.sch.santaagnes.sim.data.remote.MataPelajaranDto
import id.sch.santaagnes.sim.data.remote.UpdateStudentRequest
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.ClassRepository
import id.sch.santaagnes.sim.data.repository.GuardianRepository
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.data.repository.TeacherRepository
import id.sch.santaagnes.sim.domain.model.MasterImportResult
import id.sch.santaagnes.sim.data.repository.UserRepository
import id.sch.santaagnes.sim.domain.model.ClassFilter
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterAcademicYearDetail
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterClassMember
import id.sch.santaagnes.sim.domain.model.MasterClassMembersPage
import id.sch.santaagnes.sim.domain.model.MasterGuardian
import id.sch.santaagnes.sim.domain.model.MasterSemester
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.domain.model.MasterStudentDetail
import id.sch.santaagnes.sim.domain.model.MasterStudentGuardian
import id.sch.santaagnes.sim.domain.model.MasterTeacher
import id.sch.santaagnes.sim.domain.model.MasterTeacherDetail
import id.sch.santaagnes.sim.domain.model.MasterTeachingAssignment
import id.sch.santaagnes.sim.domain.model.MasterUser
import id.sch.santaagnes.sim.domain.model.StudentFilter
import id.sch.santaagnes.sim.domain.model.TeacherFilter
import id.sch.santaagnes.sim.domain.model.UserFilter
import id.sch.santaagnes.sim.ui.admin.master.classes.AdminClassViewModel
import id.sch.santaagnes.sim.ui.admin.master.periods.AdminAcademicYearViewModel
import id.sch.santaagnes.sim.ui.admin.master.students.AdminStudentViewModel
import id.sch.santaagnes.sim.ui.admin.master.teachers.AdminTeacherViewModel
import id.sch.santaagnes.sim.ui.admin.master.teachers.TeacherSectionMode
import id.sch.santaagnes.sim.ui.admin.master.users.AdminUserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminMasterViewModelsTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAcademicYearRepository : AcademicYearRepository {
        var semesterResult: Resource<MasterSemester> = Resource.Success(
            MasterSemester(2, 1, "2025/2026", "EVEN", "2026-01-05", "2026-06-20", true)
        )
        var years = mutableListOf(
            MasterAcademicYear(1, "2025/2026", "2025-07-14", "2026-06-20", true, 3, 2)
        )
        var listError: String? = null

        override suspend fun getActiveSemester() = semesterResult
        override suspend fun getAcademicYears(page: Int, limit: Int, search: String?): Resource<List<MasterAcademicYear>> {
            listError?.let { return Resource.Error(it) }
            return Resource.Success(years.toList())
        }
        override suspend fun getAcademicYearDetail(id: Int) = Resource.Success(
            MasterAcademicYearDetail(years.first(), listOf((semesterResult as Resource.Success).data))
        )
        override suspend fun createAcademicYear(name: String, startDate: String, endDate: String, isActive: Boolean): Resource<MasterAcademicYear> {
            val item = MasterAcademicYear(2, name, startDate, endDate, isActive)
            years.add(item)
            return Resource.Success(item)
        }
        override suspend fun updateAcademicYear(id: Int, name: String?, startDate: String?, endDate: String?) =
            Resource.Success(years.first().copy(name = name ?: years.first().name))
        override suspend fun setActiveAcademicYear(id: Int): Resource<MasterAcademicYear> {
            years = years.map { it.copy(isActive = it.id == id) }.toMutableList()
            return Resource.Success(years.first { it.id == id })
        }
        override suspend fun deleteAcademicYear(id: Int) = Resource.Success(Unit)
        override suspend fun getSemesters(academicYearId: Int?) = Resource.Success(listOf((semesterResult as Resource.Success).data))
        override suspend fun createSemester(academicYearId: Int, type: String, startDate: String, endDate: String, isActive: Boolean) =
            Resource.Success(MasterSemester(3, academicYearId, "2025/2026", type, startDate, endDate, isActive))
        override suspend fun updateSemester(id: Int, startDate: String?, endDate: String?) = semesterResult
        override suspend fun setActiveSemester(id: Int) = semesterResult
        override suspend fun deleteSemester(id: Int) = Resource.Success(Unit)
    }

    private class FakeUserRepository : UserRepository {
        var users = mutableListOf(
            MasterUser(1, "admin@test.id", "Admin", "ADMIN", "ACTIVE"),
            MasterUser(2, "user@test.id", "User", "STUDENT", "BLOCKED")
        )
        var updatedRoleCalls = mutableListOf<Pair<Int, String>>()
        var error: String? = null

        override suspend fun getUsers(page: Int, limit: Int, filter: UserFilter): Resource<Pair<List<MasterUser>, Int>> {
            error?.let { return Resource.Error(it) }
            val filtered = users.filter {
                (filter.role == null || it.role == filter.role) &&
                    (filter.status == null || it.status == filter.status)
            }
            return Resource.Success(filtered to filtered.size)
        }
        override suspend fun createUser(email: String, password: String, name: String, role: String) =
            Resource.Success(MasterUser(3, email, name, role, "ACTIVE"))
        override suspend fun updateUser(id: Int, name: String?, email: String?) =
            Resource.Success(users.first().copy(name = name ?: "Admin"))
        override suspend fun updateRole(id: Int, role: String): Resource<MasterUser> {
            updatedRoleCalls.add(id to role)
            users = users.map { if (it.id == id) it.copy(role = role) else it }.toMutableList()
            return Resource.Success(users.first { it.id == id })
        }
        override suspend fun updateStatus(id: Int, status: String): Resource<MasterUser> {
            users = users.map { if (it.id == id) it.copy(status = status, isActive = status == "ACTIVE") else it }.toMutableList()
            return Resource.Success(users.first { it.id == id })
        }
        override suspend fun resetPassword(id: Int, newPassword: String) = Resource.Success(Unit)
        override suspend fun deleteUser(id: Int) = Resource.Success(Unit)
    }

    private class FakeTeacherRepository : TeacherRepository {
        var teachers = listOf(
            MasterTeacher(1, 10, "1001", "Wali Kelas A", "wali@s.id", "ACTIVE", isHomeroomTeacher = true),
            MasterTeacher(2, 11, "1002", "Guru Biasa B", "guru@s.id", "ACTIVE", isHomeroomTeacher = false)
        )
        var assignments = mutableListOf(
            MasterTeachingAssignment(1, 2, "Guru Biasa B", "1002", 5, "MTK", "Matematika", "MANDATORY", 1, "X-A", "2025/2026", 2, "EVEN")
        )
        override suspend fun getTeachers(page: Int, limit: Int, filter: TeacherFilter): Resource<Pair<List<MasterTeacher>, Int>> {
            val res = teachers.filter { filter.isHomeroomTeacher == null || it.isHomeroomTeacher == filter.isHomeroomTeacher }
            return Resource.Success(res to res.size)
        }
        override suspend fun getTeacherDetail(id: Int) = Resource.Success(MasterTeacherDetail(teachers.first()))
        override suspend fun createTeacher(name: String, email: String, password: String, employeeNumber: String) =
            Resource.Success(MasterTeacher(3, 12, employeeNumber, name, email, "ACTIVE"))
        override suspend fun updateTeacher(id: Int, name: String?, email: String?, employeeNumber: String?) =
            Resource.Success(teachers.first())
        override suspend fun deleteTeacher(id: Int) = Resource.Success(Unit)
        override suspend fun getTeachingAssignments(semesterId: Int?, teacherId: Int?, classGroupId: Int?) =
            Resource.Success(assignments.filter { teacherId == null || it.teacherId == teacherId } to assignments.size)
        override suspend fun createTeachingAssignment(teacherId: Int, subjectId: Int, classGroupId: Int, semesterId: Int): Resource<MasterTeachingAssignment> {
            val item = MasterTeachingAssignment(2, teacherId, "Guru", "1002", subjectId, "BIO", "Biologi", "MANDATORY", classGroupId, "X-A", "2025/2026", semesterId, "EVEN")
            assignments.add(item)
            return Resource.Success(item)
        }
        override suspend fun updateTeachingAssignmentTeacher(assignmentId: Int, teacherId: Int) =
            Resource.Success(assignments.first().copy(teacherId = teacherId))
        override suspend fun deleteTeachingAssignment(assignmentId: Int) = Resource.Success(Unit)
        override suspend fun getSubjects() = Resource.Success(listOf(MataPelajaranDto(5, "MTK", "Matematika", "MANDATORY")))
        override suspend fun fileName(uri: String) = "guru.xlsx"
        override suspend fun saveImportTemplate(destination: String) = true
        override suspend fun importTeachers(uri: String) = Resource.Success(MasterImportResult(0))
    }

    private class FakeClassRepository : ClassRepository {
        var classes = mutableListOf(
            MasterClassGroup(1, "X-A", "10", 36, 1, "2025/2026", true, 1, "Wali Kelas A", "1001", 1, 2),
            MasterClassGroup(2, "X-B", "10", 36, 1, "2025/2026", true, null, null, null, 0, 0)
        )
        var members = mutableListOf(MasterClassMember(1, "2026001", "Bryan", "b@s.id"))
        val addCalls = mutableListOf<Pair<Int, List<Int>>>()
        val moveCalls = mutableListOf<Triple<Int, Int, Int>>()
        val removeCalls = mutableListOf<Pair<Int, Int>>()

        override suspend fun getClasses(page: Int, limit: Int, filter: ClassFilter) =
            Resource.Success(classes.toList() to classes.size)
        override suspend fun createClass(academicYearId: Int, name: String, gradeLevel: String, capacity: Int, homeroomTeacherId: Int?) =
            Resource.Success(MasterClassGroup(3, name, gradeLevel, capacity, academicYearId, "2025/2026", true))
        override suspend fun updateClass(id: Int, name: String?, gradeLevel: String?, capacity: Int?) =
            Resource.Success(classes.first())
        override suspend fun setHomeroomTeacher(classId: Int, homeroomTeacherId: Int?): Resource<MasterClassGroup> {
            val updated = classes.first().copy(homeroomTeacherId = homeroomTeacherId, homeroomTeacherName = if (homeroomTeacherId == null) null else "Guru")
            classes[0] = updated
            return Resource.Success(updated)
        }
        override suspend fun deleteClass(id: Int) = Resource.Success(Unit)
        override suspend fun getClassMembers(classId: Int) = Resource.Success(MasterClassMembersPage(classId, "X-A", 36, members.toList()))
        override suspend fun addClassMembers(classId: Int, studentIds: List<Int>): Resource<MasterClassMembersPage> {
            addCalls.add(classId to studentIds)
            return getClassMembers(classId)
        }
        override suspend fun moveClassMember(classId: Int, studentId: Int, targetClassGroupId: Int): Resource<Unit> {
            moveCalls.add(Triple(classId, studentId, targetClassGroupId))
            return Resource.Success(Unit)
        }
        override suspend fun removeClassMember(classId: Int, studentId: Int): Resource<Unit> {
            removeCalls.add(classId to studentId)
            return Resource.Success(Unit)
        }
    }

    private class FakeStudentRepository : StudentRepository {
        var students = listOf(MasterStudent(1, 20, "Bryan", "b@s.id", "2026001", "ACTIVE", birthDate = "2008-05-12"))
        var lastCreateRequest: CreateStudentRequest? = null
        override suspend fun getStudents(page: Int, limit: Int, filter: StudentFilter) = Resource.Success(students to students.size)
        override suspend fun getStudentDetail(id: Int) = Resource.Success(
            MasterStudentDetail(students.first(), listOf(MasterStudentGuardian(1, "Ayah", "Wali Satu", "0811", "w@s.id")))
        )
        override suspend fun createStudent(request: CreateStudentRequest): Resource<MasterStudent> {
            lastCreateRequest = request
            return Resource.Success(students.first())
        }
        override suspend fun updateStudent(id: Int, request: UpdateStudentRequest) = Resource.Success(students.first())
        override suspend fun deleteStudent(id: Int) = Resource.Success(Unit)
        override suspend fun linkGuardian(studentId: Int, guardianId: Int, relationship: String) = Resource.Success(Unit)
        override suspend fun unlinkGuardian(studentId: Int, guardianId: Int) = Resource.Success(Unit)
        override suspend fun fileName(uri: String) = "siswa.xlsx"
        override suspend fun saveImportTemplate(destination: String) = true
        override suspend fun importStudents(uri: String) = Resource.Success(MasterImportResult(0))
    }

    private class FakeGuardianRepository : GuardianRepository {
        override suspend fun getGuardians(search: String?) = Resource.Success(
            listOf(MasterGuardian(1, 30, "Wali Satu", "w@s.id", "0811", "ACTIVE", 1))
        )
        override suspend fun createGuardian(name: String, email: String, password: String, phone: String) =
            Resource.Success(MasterGuardian(2, 31, name, email, phone, "ACTIVE", 0))
        override suspend fun updateGuardian(id: Int, name: String?, email: String?, phone: String?) =
            Resource.Success(MasterGuardian(1, 30, name ?: "Wali", email ?: "w@s.id", phone ?: "0811", "ACTIVE", 1))
        override suspend fun deleteGuardian(id: Int) = Resource.Success(Unit)
    }

    @Test
    fun adminUserViewModelLoadsAndUpdatesRole() = runTest {
        val repo = FakeUserRepository()
        val vm = AdminUserViewModel(repo, FakeAcademicYearRepository())

        assertEquals(2, vm.uiState.value.users.size)
        assertEquals(1, vm.uiState.value.activeCount)
        assertEquals(1, vm.uiState.value.inactiveCount)

        vm.updateUser(repo.users.last(), "User Updated", "user@test.id", "TEACHER")
        assertEquals(listOf(2 to "TEACHER"), repo.updatedRoleCalls)
    }

    @Test
    fun adminUserViewModelShowsErrorOnFailure() = runTest {
        val repo = FakeUserRepository().apply { error = "Gagal koneksi" }
        val vm = AdminUserViewModel(repo, FakeAcademicYearRepository())

        assertEquals("Gagal koneksi", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun adminStudentViewModelCreatesWithBirthDateAndLoadsGuardians() = runTest {
        val studentRepo = FakeStudentRepository()
        val classRepo = FakeClassRepository()
        val vm = AdminStudentViewModel(studentRepo, classRepo, FakeGuardianRepository(), FakeAcademicYearRepository())

        vm.createStudent("Siswa Baru", "s@s.id", "password123", "2026999", "2009-01-15", "0812", classGroupId = 2)
        assertEquals("2009-01-15", studentRepo.lastCreateRequest?.birthDate)
        assertEquals(listOf(2 to listOf(1)), classRepo.addCalls)

        val unassigned = MasterStudent(5, 25, "Tanpa Kelas", "tk@s.id", "2026005", "ACTIVE", classId = null)
        vm.assignStudentClass(unassigned, 1)
        assertEquals(listOf(2 to listOf(1), 1 to listOf(5)), classRepo.addCalls)

        val inClassA = unassigned.copy(classId = 1, className = "X-A")
        vm.assignStudentClass(inClassA, 2)
        assertEquals(listOf(Triple(1, 5, 2)), classRepo.moveCalls)

        vm.assignStudentClass(inClassA, null)
        assertEquals(listOf(1 to 5), classRepo.removeCalls)

        vm.loadGuardianPanel(1)
        assertEquals(1, vm.uiState.value.selectedStudentGuardians.size)
        assertEquals("Wali Satu", vm.uiState.value.selectedStudentGuardians.first().name)
    }

    @Test
    fun adminTeacherViewModelFiltersHomeroomAndSwitchesToAssignments() = runTest {
        val teacherRepo = FakeTeacherRepository()
        val vm = AdminTeacherViewModel(teacherRepo, FakeClassRepository(), FakeAcademicYearRepository())

        assertEquals(2, vm.uiState.value.teachers.size)
        assertEquals(1, vm.uiState.value.homeroomCount)
        assertEquals(1, vm.uiState.value.regularCount)

        vm.setHomeroomFilter(true)
        assertEquals(1, vm.uiState.value.teachers.size)
        assertTrue(vm.uiState.value.teachers.first().isHomeroomTeacher)

        vm.setMode(TeacherSectionMode.ASSIGNMENTS, null)
        assertEquals(1, vm.uiState.value.assignments.size)
        vm.createTeachingAssignment(2, 5, 1)
        assertEquals(2, vm.uiState.value.assignments.size)
    }

    @Test
    fun adminClassAndAcademicYearViewModelsManageHomeroomAndPeriods() = runTest {
        val classRepo = FakeClassRepository()
        val yearRepo = FakeAcademicYearRepository()

        val classVm = AdminClassViewModel(classRepo, yearRepo, FakeTeacherRepository(), FakeStudentRepository())
        assertEquals(1, classVm.uiState.value.withHomeroomCount)
        classVm.setHomeroomTeacher(1, null)
        assertNull(classVm.uiState.value.classes.first().homeroomTeacherId)
        classVm.loadClassMembers(1)
        assertEquals(1, classVm.uiState.value.selectedMembersPage?.members?.size)

        val periodVm = AdminAcademicYearViewModel(yearRepo)
        periodVm.createYear("2026/2027", "2026-07-13", "2027-06-19", false)
        assertEquals(2, periodVm.uiState.value.years.size)
        periodVm.setActiveYear(2)
        assertTrue(periodVm.uiState.value.years.first { it.id == 2 }.isActive)
    }
}
