package id.sch.santaagnes.sim.notification

import id.sch.santaagnes.sim.domain.model.NotificationType
import id.sch.santaagnes.sim.domain.model.PushPayload
import id.sch.santaagnes.sim.ui.notification.NotificationListViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationListViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun payload(title: String) = PushPayload(NotificationType.ANNOUNCEMENT, title, "Isi $title", null)

    @Test
    fun startsLoadingThenShowsEmptyState() = runTest {
        val viewModel = NotificationListViewModel(FakeNotificationRepository())
        assertTrue(viewModel.uiState.value.isLoading)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun showsNewestFirstAndCountsUnread() = runTest {
        val repository = FakeNotificationRepository()
        val viewModel = NotificationListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        repository.save(payload("Lama"))
        repository.save(payload("Baru"))

        val state = viewModel.uiState.value
        assertEquals(listOf("Baru", "Lama"), state.items?.map { it.title })
        assertEquals(2, state.unreadCount)
    }

    @Test
    fun markReadClearsOnlyThatItem() = runTest {
        val repository = FakeNotificationRepository()
        val viewModel = NotificationListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        repository.save(payload("A"))
        val second = repository.save(payload("B"))!!

        viewModel.markRead(second.id)

        assertEquals(1, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun markAllReadClearsEverything() = runTest {
        val repository = FakeNotificationRepository()
        val viewModel = NotificationListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        repository.save(payload("A"))
        repository.save(payload("B"))

        viewModel.markAllRead()

        assertEquals(0, viewModel.uiState.value.unreadCount)
    }
}
