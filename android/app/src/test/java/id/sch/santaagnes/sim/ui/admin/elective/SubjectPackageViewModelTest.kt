package id.sch.santaagnes.sim.ui.admin.elective

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.ElectiveRepository
import id.sch.santaagnes.sim.domain.model.GradePredicateItem
import id.sch.santaagnes.sim.domain.model.GradePredicateScale
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterAcademicYearDetail
import id.sch.santaagnes.sim.domain.model.MasterSemester
import id.sch.santaagnes.sim.domain.model.PackageSubject
import id.sch.santaagnes.sim.domain.model.SubjectPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectPackageViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsActiveYearAndPackagesAndSupportsCreateUpdateDelete() = runTest {
        val repo = FakeElectiveRepository()
        val yearRepo = FakeAcademicYearRepository()
        val vm = SubjectPackageViewModel(repo, yearRepo)

        val initial = vm.uiState.value
        assertEquals(2, initial.selectedYearId)
        assertEquals(1, initial.packages.size)
        assertEquals("Paket MIPA", initial.packages.first().name)
        assertEquals(2, initial.subjects.size)

        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { messages.add(it) } }

        vm.createPackage(2, "Paket IPS", 30, listOf(11))
        assertEquals(2, vm.uiState.value.packages.size)

        val createdId = vm.uiState.value.packages.last().id
        vm.updatePackage(createdId, "Paket IPS Lanjut", 34, listOf(10, 11))
        assertEquals(
            "Paket IPS Lanjut",
            vm.uiState.value.packages.first { it.id == createdId }.name
        )

        vm.deletePackage(createdId)
        assertEquals(1, vm.uiState.value.packages.size)
        job.cancel()

        assertTrue(messages.any { it.contains("berhasil ditambahkan") })
        assertTrue(messages.any { it.contains("berhasil diperbarui") })
        assertTrue(messages.any { it.contains("berhasil dihapus") })
    }

    @Test
    fun filteringByAcademicYearAndSearchQueriesRepository() = runTest {
        val repo = FakeElectiveRepository()
        val vm = SubjectPackageViewModel(repo, FakeAcademicYearRepository())

        vm.setAcademicYearFilter(1)
        assertTrue(vm.uiState.value.packages.isEmpty())

        vm.setAcademicYearFilter(2)
        vm.setSearchQuery("MIPA")
        assertEquals(1, vm.uiState.value.packages.size)
        assertEquals("MIPA", repo.lastSearch)
    }

    private class FakeElectiveRepository : ElectiveRepository {
        private val allSubjects = listOf(
            PackageSubject(10, "BIO", "Biologi", "Pilihan"),
            PackageSubject(11, "EKO", "Ekonomi", "Pilihan")
        )
        private val packages = mutableListOf(
            SubjectPackage(
                id = 1,
                academicYearId = 2,
                academicYearName = "2026/2027",
                isAcademicYearActive = true,
                name = "Paket MIPA",
                capacity = 36,
                subjects = listOf(allSubjects[0]),
                selectionCount = 0
            )
        )
        var lastSearch: String? = null

        override suspend fun getSubjectPackages(
            academicYearId: Int?,
            search: String?
        ): Resource<List<SubjectPackage>> {
            lastSearch = search
            val q = search?.trim()?.lowercase().orEmpty()
            val filtered = packages.filter { pkg ->
                (academicYearId == null || pkg.academicYearId == academicYearId) &&
                    (q.isEmpty() || pkg.name.lowercase().contains(q))
            }
            return Resource.Success(filtered)
        }

        override suspend fun createSubjectPackage(
            academicYearId: Int,
            name: String,
            capacity: Int,
            subjectIds: List<Int>
        ): Resource<SubjectPackage> {
            val created = SubjectPackage(
                id = (packages.maxOfOrNull { it.id } ?: 0) + 1,
                academicYearId = academicYearId,
                academicYearName = "2026/2027",
                isAcademicYearActive = true,
                name = name,
                capacity = capacity,
                subjects = allSubjects.filter { it.id in subjectIds },
                selectionCount = 0
            )
            packages.add(created)
            return Resource.Success(created)
        }

        override suspend fun updateSubjectPackage(
            id: Int,
            name: String?,
            capacity: Int?,
            subjectIds: List<Int>?
        ): Resource<SubjectPackage> {
            val idx = packages.indexOfFirst { it.id == id }
            val current = packages[idx]
            val updated = current.copy(
                name = name ?: current.name,
                capacity = capacity ?: current.capacity,
                subjects = subjectIds?.let { ids -> allSubjects.filter { it.id in ids } } ?: current.subjects
            )
            packages[idx] = updated
            return Resource.Success(updated)
        }

        override suspend fun deleteSubjectPackage(id: Int): Resource<Unit> {
            packages.removeAll { it.id == id }
            return Resource.Success(Unit)
        }

        override suspend fun getSubjects() = Resource.Success(allSubjects)
    }

    private class FakeAcademicYearRepository : AcademicYearRepository {
        private val years = listOf(
            MasterAcademicYear(1, "2025/2026", "2025-07-14", "2026-06-20", isActive = false),
            MasterAcademicYear(2, "2026/2027", "2026-07-13", "2027-06-30", isActive = true)
        )

        override suspend fun getAcademicYears(page: Int, limit: Int, search: String?) =
            Resource.Success(years)
        override suspend fun getActiveSemester(): Resource<MasterSemester> = error("unused")
        override suspend fun getAcademicYearDetail(id: Int): Resource<MasterAcademicYearDetail> = error("unused")
        override suspend fun createAcademicYear(name: String, startDate: String, endDate: String, isActive: Boolean): Resource<MasterAcademicYear> = error("unused")
        override suspend fun updateAcademicYear(id: Int, name: String?, startDate: String?, endDate: String?): Resource<MasterAcademicYear> = error("unused")
        override suspend fun setActiveAcademicYear(id: Int): Resource<MasterAcademicYear> = error("unused")
        override suspend fun deleteAcademicYear(id: Int): Resource<Unit> = error("unused")
        override suspend fun getSemesters(academicYearId: Int?): Resource<List<MasterSemester>> = error("unused")
        override suspend fun createSemester(academicYearId: Int, type: String, startDate: String, endDate: String, isActive: Boolean): Resource<MasterSemester> = error("unused")
        override suspend fun updateSemester(id: Int, startDate: String?, endDate: String?): Resource<MasterSemester> = error("unused")
        override suspend fun setActiveSemester(id: Int): Resource<MasterSemester> = error("unused")
        override suspend fun deleteSemester(id: Int): Resource<Unit> = error("unused")
        override suspend fun getGradePredicates(academicYearId: Int): Resource<GradePredicateScale> = error("unused")
        override suspend fun replaceGradePredicates(academicYearId: Int, predicates: List<GradePredicateItem>): Resource<GradePredicateScale> = error("unused")
    }
}
