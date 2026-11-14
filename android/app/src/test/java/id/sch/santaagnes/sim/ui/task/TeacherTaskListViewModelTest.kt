package id.sch.santaagnes.sim.ui.task

import id.sch.santaagnes.sim.ui.task.FakeTaskRepository.Companion.task
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
class TeacherTaskListViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshShowsFirstPageAndHasMore() = runTest {
        val repository = FakeTaskRepository().apply { pages = listOf(listOf(task(1), task(2)), listOf(task(3))) }
        val viewModel = TeacherTaskListViewModel(repository)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf(1, 2), state.tasks.map { it.id })
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadMoreAppendsNextPageWithoutDuplicates() = runTest {
        // Tugas 2 muncul lagi di halaman kedua karena daftar bergeser di server.
        val repository = FakeTaskRepository().apply { pages = listOf(listOf(task(1), task(2)), listOf(task(2), task(3))) }
        val viewModel = TeacherTaskListViewModel(repository)
        viewModel.refresh()

        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertEquals(listOf(1, 2, 3), state.tasks.map { it.id })
        assertFalse(state.hasMore)
        assertEquals(listOf(1, 2), repository.requestedPages)

        viewModel.loadMore()
        assertEquals(listOf(1, 2), repository.requestedPages)
    }

    @Test
    fun refreshFailureShowsMessage() = runTest {
        val repository = FakeTaskRepository().apply { tasksError = "Tidak dapat terhubung ke server." }
        val viewModel = TeacherTaskListViewModel(repository)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals("Tidak dapat terhubung ke server.", state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
    }

    @Test
    fun emptyListIsEmptyState() = runTest {
        val viewModel = TeacherTaskListViewModel(FakeTaskRepository())

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun deleteRemovesTaskFromList() = runTest {
        val repository = FakeTaskRepository().apply { pages = listOf(listOf(task(1), task(2))) }
        val viewModel = TeacherTaskListViewModel(repository)
        viewModel.refresh()

        viewModel.delete(1)

        assertEquals(listOf(2), viewModel.uiState.value.tasks.map { it.id })
        assertEquals(listOf(1), repository.deleted)
    }

    @Test
    fun deleteFailureKeepsTaskAndShowsMessage() = runTest {
        val repository = FakeTaskRepository().apply {
            pages = listOf(listOf(task(1)))
            deleteError = "Terjadi kesalahan pada server."
        }
        val viewModel = TeacherTaskListViewModel(repository)
        viewModel.refresh()

        viewModel.delete(1)

        assertEquals(listOf(1), viewModel.uiState.value.tasks.map { it.id })
        assertEquals("Terjadi kesalahan pada server.", viewModel.uiState.value.errorMessage)
    }
}
