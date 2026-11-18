package id.sch.santaagnes.sim.ui.admin.master

import androidx.lifecycle.SavedStateHandle
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateStudentRequest
import id.sch.santaagnes.sim.data.remote.MataPelajaranDto
import id.sch.santaagnes.sim.data.remote.UpdateStudentRequest
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.data.repository.TeacherRepository
import id.sch.santaagnes.sim.domain.model.ImportRowError
import id.sch.santaagnes.sim.domain.model.MasterImportResult
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.domain.model.MasterStudentDetail
import id.sch.santaagnes.sim.domain.model.MasterTeacher
import id.sch.santaagnes.sim.domain.model.MasterTeacherDetail
import id.sch.santaagnes.sim.domain.model.MasterTeachingAssignment
import id.sch.santaagnes.sim.domain.model.StudentFilter
import id.sch.santaagnes.sim.domain.model.TeacherFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class MasterImportViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun studentImportSuccessShowsCountsAndUsesStudentRepo() = runTest {
        val studentRepo = StubStudentRepository().apply {
            importResponse = Resource.Success(MasterImportResult(imported = 5, assignedToClass = 3))
        }
        val teacherRepo = StubTeacherRepository()
        val vm = MasterImportViewModel(
            savedStateHandle = SavedStateHandle(mapOf(MasterImportViewModel.ARG_IMPORT_TYPE to "STUDENT")),
            studentRepository = studentRepo,
            teacherRepository = teacherRepo
        )

        assertFalse(vm.uiState.value.canImport)
        vm.onFilePicked("content://doc/siswa")
        assertEquals("siswa.xlsx", vm.uiState.value.fileName)
        assertTrue(vm.uiState.value.canImport)

        vm.import()

        assertEquals(listOf("content://doc/siswa"), studentRepo.importedUris)
        assertTrue(teacherRepo.importedUris.isEmpty())
        assertEquals(MasterImportResult(imported = 5, assignedToClass = 3), vm.uiState.value.result)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun teacherImportSuccessUsesTeacherRepo() = runTest {
        val studentRepo = StubStudentRepository()
        val teacherRepo = StubTeacherRepository().apply {
            importResponse = Resource.Success(MasterImportResult(imported = 4))
        }
        val vm = MasterImportViewModel(
            savedStateHandle = SavedStateHandle(mapOf(MasterImportViewModel.ARG_IMPORT_TYPE to "TEACHER")),
            studentRepository = studentRepo,
            teacherRepository = teacherRepo
        )

        vm.onFilePicked("content://doc/guru")
        assertEquals("guru.xlsx", vm.uiState.value.fileName)
        vm.import()

        assertEquals(listOf("content://doc/guru"), teacherRepo.importedUris)
        assertTrue(studentRepo.importedUris.isEmpty())
        assertEquals(MasterImportResult(imported = 4, assignedToClass = 0), vm.uiState.value.result)
    }

    @Test
    fun rejectedImportPopulatesRowErrorsAndDistinctRowCount() = runTest {
        val rowErrors = listOf(
            ImportRowError(row = 2, column = "NIS", message = "NIS sudah terdaftar."),
            ImportRowError(row = 2, column = "Email", message = "Format email tidak valid."),
            ImportRowError(row = 5, column = "Kelas", message = "Kapasitas kelas penuh.")
        )
        val studentRepo = StubStudentRepository().apply {
            importResponse = Resource.Error(
                message = "Impor dibatalkan: 3 kesalahan di 2 baris.",
                importErrors = rowErrors
            )
        }
        val vm = MasterImportViewModel(SavedStateHandle(), studentRepo, StubTeacherRepository())

        vm.onFilePicked("content://doc/bad")
        vm.import()

        assertNull(vm.uiState.value.result)
        assertEquals("Impor dibatalkan: 3 kesalahan di 2 baris.", vm.uiState.value.errorMessage)
        assertEquals(3, vm.uiState.value.importErrors.size)
        assertEquals(2, vm.uiState.value.errorRowCount)
        assertFalse(vm.uiState.value.isUploading)

        // Memilih berkas baru membersihkan error & hasil sebelumnya.
        vm.onFilePicked("content://doc/fixed")
        assertNull(vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.importErrors.isEmpty())
    }

    @Test
    fun templateSavedEmitsStatusFromActiveTypeRepository() = runTest {
        val studentRepo = StubStudentRepository().apply { templateSavedResult = true }
        val teacherRepo = StubTeacherRepository().apply { templateSavedResult = false }

        val studentVm = MasterImportViewModel(
            SavedStateHandle(mapOf(MasterImportViewModel.ARG_IMPORT_TYPE to "STUDENT")),
            studentRepo,
            teacherRepo
        )
        val teacherVm = MasterImportViewModel(
            SavedStateHandle(mapOf(MasterImportViewModel.ARG_IMPORT_TYPE to "TEACHER")),
            studentRepo,
            teacherRepo
        )

        val studentEvents = mutableListOf<Boolean>()
        val teacherEvents = mutableListOf<Boolean>()
        val job1 = launch(UnconfinedTestDispatcher(testScheduler)) {
            studentVm.templateSaved.collect { studentEvents.add(it) }
        }
        val job2 = launch(UnconfinedTestDispatcher(testScheduler)) {
            teacherVm.templateSaved.collect { teacherEvents.add(it) }
        }

        studentVm.onTemplateDestinationPicked("content://out/siswa.xlsx")
        teacherVm.onTemplateDestinationPicked("content://out/guru.xlsx")

        assertEquals(listOf(true), studentEvents)
        assertEquals(listOf(false), teacherEvents)
        job1.cancel()
        job2.cancel()
    }

    private class StubStudentRepository : StudentRepository {
        val importedUris = mutableListOf<String>()
        var templateSavedResult = true
        var importResponse: Resource<MasterImportResult> = Resource.Success(MasterImportResult(0))

        override suspend fun getStudents(page: Int, limit: Int, filter: StudentFilter) =
            Resource.Success(emptyList<MasterStudent>() to 0)
        override suspend fun getStudentDetail(id: Int): Resource<MasterStudentDetail> = error("unused")
        override suspend fun createStudent(request: CreateStudentRequest): Resource<MasterStudent> = error("unused")
        override suspend fun updateStudent(id: Int, request: UpdateStudentRequest): Resource<MasterStudent> = error("unused")
        override suspend fun deleteStudent(id: Int) = Resource.Success(Unit)
        override suspend fun linkGuardian(studentId: Int, guardianId: Int, relationship: String) = Resource.Success(Unit)
        override suspend fun unlinkGuardian(studentId: Int, guardianId: Int) = Resource.Success(Unit)
        override suspend fun fileName(uri: String) = "siswa.xlsx"
        override suspend fun saveImportTemplate(destination: String) = templateSavedResult
        override suspend fun importStudents(uri: String): Resource<MasterImportResult> {
            importedUris.add(uri)
            return importResponse
        }
    }

    private class StubTeacherRepository : TeacherRepository {
        val importedUris = mutableListOf<String>()
        var templateSavedResult = true
        var importResponse: Resource<MasterImportResult> = Resource.Success(MasterImportResult(0))

        override suspend fun getTeachers(page: Int, limit: Int, filter: TeacherFilter) =
            Resource.Success(emptyList<MasterTeacher>() to 0)
        override suspend fun getTeacherDetail(id: Int): Resource<MasterTeacherDetail> = error("unused")
        override suspend fun createTeacher(name: String, email: String, password: String, employeeNumber: String): Resource<MasterTeacher> = error("unused")
        override suspend fun updateTeacher(id: Int, name: String?, email: String?, employeeNumber: String?): Resource<MasterTeacher> = error("unused")
        override suspend fun deleteTeacher(id: Int) = Resource.Success(Unit)
        override suspend fun getTeachingAssignments(semesterId: Int?, teacherId: Int?, classGroupId: Int?) =
            Resource.Success(emptyList<MasterTeachingAssignment>() to 0)
        override suspend fun createTeachingAssignment(teacherId: Int, subjectId: Int, classGroupId: Int, semesterId: Int): Resource<MasterTeachingAssignment> = error("unused")
        override suspend fun updateTeachingAssignmentTeacher(assignmentId: Int, teacherId: Int): Resource<MasterTeachingAssignment> = error("unused")
        override suspend fun deleteTeachingAssignment(assignmentId: Int) = Resource.Success(Unit)
        override suspend fun getSubjects() = Resource.Success(emptyList<MataPelajaranDto>())
        override suspend fun fileName(uri: String) = "guru.xlsx"
        override suspend fun saveImportTemplate(destination: String) = templateSavedResult
        override suspend fun importTeachers(uri: String): Resource<MasterImportResult> {
            importedUris.add(uri)
            return importResponse
        }
    }
}
