package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.ui.admin.FakeAdminScheduleRepository.Companion.change
import id.sch.santaagnes.sim.ui.admin.schedule.ChangeListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeListViewModelTest {

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
        val repository = FakeAdminScheduleRepository().apply { changePages = listOf(listOf(change(1), change(2))) }
        val viewModel = ChangeListViewModel(repository)

        viewModel.refresh()

        assertEquals(listOf(1, 2), viewModel.uiState.value.changes.map { it.id })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun emptyResultIsEmptyState() = runTest {
        val viewModel = ChangeListViewModel(FakeAdminScheduleRepository())

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun failureShowsMessage() = runTest {
        val repository = FakeAdminScheduleRepository().apply { changesError = "Tidak dapat terhubung ke server." }
        val viewModel = ChangeListViewModel(repository)

        viewModel.refresh()

        assertEquals("Tidak dapat terhubung ke server.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun loadMoreAppendsWithoutDuplicates() = runTest {
        val repository = FakeAdminScheduleRepository().apply {
            changePages = listOf(listOf(change(1)), listOf(change(1), change(2)))
        }
        val viewModel = ChangeListViewModel(repository)
        viewModel.refresh()

        viewModel.loadMore()

        assertEquals(listOf(1, 2), viewModel.uiState.value.changes.map { it.id })
    }

    @Test
    fun selectingTypeReloadsWithThatType() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = ChangeListViewModel(repository)
        viewModel.refresh()

        viewModel.selectType(ChangeType.CANCEL)

        assertEquals(1 to ChangeType.CANCEL, repository.requestedChangePages.last())
    }

    @Test
    fun deleteRemovesChange() = runTest {
        val repository = FakeAdminScheduleRepository().apply { changePages = listOf(listOf(change(1), change(2))) }
        val viewModel = ChangeListViewModel(repository)
        viewModel.refresh()

        viewModel.delete(1)

        assertEquals(listOf(1), repository.deletedChanges)
        assertEquals(listOf(2), viewModel.uiState.value.changes.map { it.id })
    }

    @Test
    fun deleteFailureKeepsListAndShowsMessage() = runTest {
        val repository = FakeAdminScheduleRepository().apply {
            changePages = listOf(listOf(change(1)))
            deleteError = "Perubahan tidak dapat dihapus."
        }
        val viewModel = ChangeListViewModel(repository)
        viewModel.refresh()

        viewModel.delete(1)

        assertEquals(1, viewModel.uiState.value.changes.size)
        assertEquals("Perubahan tidak dapat dihapus.", viewModel.uiState.value.errorMessage)
    }
}
