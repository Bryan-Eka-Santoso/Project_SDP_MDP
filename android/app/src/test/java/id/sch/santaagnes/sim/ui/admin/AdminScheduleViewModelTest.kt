package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.domain.model.SlotFilter
import id.sch.santaagnes.sim.ui.admin.FakeAdminScheduleRepository.Companion.slot
import id.sch.santaagnes.sim.ui.admin.schedule.AdminScheduleViewModel
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
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class AdminScheduleViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshLoadsFirstPage() = runTest {
        val repository = FakeAdminScheduleRepository().apply { slotPages = listOf(listOf(slot(1), slot(2))) }
        val viewModel = AdminScheduleViewModel(repository)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf(1, 2), state.slots.map { it.id })
        assertFalse(state.isLoading)
        assertFalse(state.hasMore)
    }

    @Test
    fun emptyResultIsEmptyState() = runTest {
        val viewModel = AdminScheduleViewModel(FakeAdminScheduleRepository())

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun refreshFailureShowsMessageAndNotEmpty() = runTest {
        val repository = FakeAdminScheduleRepository().apply { slotsError = "Tidak dapat terhubung ke server." }
        val viewModel = AdminScheduleViewModel(repository)

        viewModel.refresh()

        assertEquals("Tidak dapat terhubung ke server.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun loadMoreAppendsNextPageWithoutDuplicates() = runTest {
        val repository = FakeAdminScheduleRepository().apply {
            slotPages = listOf(listOf(slot(1), slot(2)), listOf(slot(2), slot(3)))
        }
        val viewModel = AdminScheduleViewModel(repository)
        viewModel.refresh()

        viewModel.loadMore()

        assertEquals(listOf(1, 2, 3), viewModel.uiState.value.slots.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun changingFilterReloadsFromFirstPageWithThatFilter() = runTest {
        val repository = FakeAdminScheduleRepository().apply { slotPages = listOf(listOf(slot(1))) }
        val viewModel = AdminScheduleViewModel(repository)
        viewModel.refresh()

        viewModel.selectDay(DayOfWeek.TUESDAY)
        viewModel.selectClassGroup(5)

        assertEquals(1 to SlotFilter(DayOfWeek.TUESDAY, 5, null), repository.requestedSlotFilters.last())
    }

    @Test
    fun selectingSameFilterDoesNotReload() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = AdminScheduleViewModel(repository)
        viewModel.refresh()
        val calls = repository.requestedSlotFilters.size

        viewModel.selectDay(null)

        assertEquals(calls, repository.requestedSlotFilters.size)
    }

    @Test
    fun deleteRemovesSlotFromList() = runTest {
        val repository = FakeAdminScheduleRepository().apply { slotPages = listOf(listOf(slot(1), slot(2))) }
        val viewModel = AdminScheduleViewModel(repository)
        viewModel.refresh()

        viewModel.delete(1)

        assertEquals(listOf(1), repository.deletedSlots)
        assertEquals(listOf(2), viewModel.uiState.value.slots.map { it.id })
    }

    @Test
    fun deleteFailureKeepsListAndShowsMessage() = runTest {
        val repository = FakeAdminScheduleRepository().apply {
            slotPages = listOf(listOf(slot(1)))
            deleteError = "Jadwal tidak dapat dihapus."
        }
        val viewModel = AdminScheduleViewModel(repository)
        viewModel.refresh()

        viewModel.delete(1)

        assertEquals(1, viewModel.uiState.value.slots.size)
        assertEquals("Jadwal tidak dapat dihapus.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun filterOptionsComeFromLookups() = runTest {
        val viewModel = AdminScheduleViewModel(FakeAdminScheduleRepository())

        assertEquals(listOf("X-1", "X-2"), viewModel.uiState.value.classGroups.map { it.name })
        assertEquals(listOf("Guru Dua", "Guru Satu"), viewModel.uiState.value.teachers.map { it.name })
    }

    @Test
    fun lookupFailureIsSilent() = runTest {
        val repository = FakeAdminScheduleRepository().apply { lookupsResult = Resource.Error("gagal") }

        val viewModel = AdminScheduleViewModel(repository)

        assertTrue(viewModel.uiState.value.classGroups.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
