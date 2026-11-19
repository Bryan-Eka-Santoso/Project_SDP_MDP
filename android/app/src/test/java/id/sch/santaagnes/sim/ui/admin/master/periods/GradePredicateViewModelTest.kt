package id.sch.santaagnes.sim.ui.admin.master.periods

import androidx.lifecycle.SavedStateHandle
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.domain.model.GradePredicateItem
import id.sch.santaagnes.sim.domain.model.GradePredicateScale
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterAcademicYearDetail
import id.sch.santaagnes.sim.domain.model.MasterSemester
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradePredicateViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsPredicatesSortedDescendingAndDetectsZeroRule() = runTest {
        val repo = FakeYearRepo(
            initial = listOf(
                GradePredicateItem(1, "C", 70.0, "Cukup"),
                GradePredicateItem(2, "A", 90.0, "Sangat Baik"),
                GradePredicateItem(3, "D", 0.0, "Perlu Bimbingan")
            )
        )
        val vm = GradePredicateViewModel(
            SavedStateHandle(
                mapOf(
                    GradePredicateViewModel.ARG_ACADEMIC_YEAR_ID to 5,
                    GradePredicateViewModel.ARG_ACADEMIC_YEAR_NAME to "2026/2027"
                )
            ),
            repo
        )

        val state = vm.uiState.value
        assertEquals(listOf("A", "C", "D"), state.items.map { it.label })
        assertTrue(state.hasZeroThreshold)
        assertTrue(state.canSave)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun upsertValidatesDuplicatesBoundsAndDecimalsBeforeUpdatingDraft() = runTest {
        val repo = FakeYearRepo(
            initial = listOf(
                GradePredicateItem(1, "A", 90.0, null),
                GradePredicateItem(2, "D", 0.0, null)
            )
        )
        val vm = GradePredicateViewModel(
            SavedStateHandle(mapOf(GradePredicateViewModel.ARG_ACADEMIC_YEAR_ID to 5)),
            repo
        )

        assertNotNull(vm.upsertItem(null, "", "80", ""))
        assertNotNull(vm.upsertItem(null, "a", "80", ""))
        assertNotNull(vm.upsertItem(null, "B", "90", ""))
        assertNotNull(vm.upsertItem(null, "B", "80.123", ""))
        assertNotNull(vm.upsertItem(null, "B", "101", ""))

        val okError = vm.upsertItem(null, "B", "79.5", "Baik")
        assertNull(okError)
        assertEquals(listOf("A", "B", "D"), vm.uiState.value.items.map { it.label })
        assertTrue(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun removingZeroThresholdDisablesSaveUntilRestoredOrDefaultApplied() = runTest {
        val repo = FakeYearRepo(
            initial = listOf(
                GradePredicateItem(1, "A", 90.0, null),
                GradePredicateItem(2, "D", 0.0, null)
            )
        )
        val vm = GradePredicateViewModel(
            SavedStateHandle(mapOf(GradePredicateViewModel.ARG_ACADEMIC_YEAR_ID to 5)),
            repo
        )

        // Index 1 adalah D (0) karena diurutkan menurun.
        vm.removeItem(1)
        assertFalse(vm.uiState.value.hasZeroThreshold)
        assertFalse(vm.uiState.value.canSave)

        vm.applyDefaultScale()
        assertTrue(vm.uiState.value.hasZeroThreshold)
        assertTrue(vm.uiState.value.canSave)

        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { messages.add(it) } }
        vm.saveScale()
        job.cancel()

        assertEquals(4, repo.saved?.size)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertEquals(listOf("Skala predikat nilai berhasil disimpan"), messages)
    }

    private class FakeYearRepo(initial: List<GradePredicateItem>) : AcademicYearRepository {
        var predicates = initial
        var saved: List<GradePredicateItem>? = null

        override suspend fun getGradePredicates(academicYearId: Int) =
            Resource.Success(GradePredicateScale(academicYearId, "2026/2027", predicates))

        override suspend fun replaceGradePredicates(
            academicYearId: Int,
            predicates: List<GradePredicateItem>
        ): Resource<GradePredicateScale> {
            saved = predicates
            this.predicates = predicates
            return Resource.Success(GradePredicateScale(academicYearId, "2026/2027", predicates))
        }

        override suspend fun getActiveSemester(): Resource<MasterSemester> = error("unused")
        override suspend fun getAcademicYears(page: Int, limit: Int, search: String?): Resource<List<MasterAcademicYear>> = error("unused")
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
    }
}
