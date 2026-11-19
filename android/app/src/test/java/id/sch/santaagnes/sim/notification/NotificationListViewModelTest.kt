package id.sch.santaagnes.sim.notification

import id.sch.santaagnes.sim.data.remote.AnnouncementAuthorDto
import id.sch.santaagnes.sim.data.remote.AnnouncementDto
import id.sch.santaagnes.sim.data.remote.IdNameDto
import id.sch.santaagnes.sim.data.repository.toSyncedAnnouncementEntity
import id.sch.santaagnes.sim.domain.model.NotificationType
import id.sch.santaagnes.sim.domain.model.PushPayload
import id.sch.santaagnes.sim.ui.notification.NotificationListViewModel
import org.junit.Assert.assertNull
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

    @Test
    fun refreshSyncsAnnouncementsIntoNotificationListWithoutDuplicates() = runTest {
        val repository = FakeNotificationRepository().apply {
            pendingSyncedAnnouncements += payload("Pengumuman: Libur Semester")
        }
        val viewModel = NotificationListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        assertEquals(1, viewModel.uiState.value.items?.size)
        assertEquals("Pengumuman: Libur Semester", viewModel.uiState.value.items?.first()?.title)

        viewModel.refresh()
        assertEquals(1, viewModel.uiState.value.items?.size)
    }

    @Test
    fun syncedAnnouncementSkipsSelfAuthoredAndFormatsSchoolOrClassTitle() {
        val own = AnnouncementDto(
            id = 10,
            title = "Info Admin",
            content = "Konten",
            scope = "SCHOOL",
            author = AnnouncementAuthorDto(id = 1, name = "Admin", role = "ADMIN"),
            date = "2026-11-19"
        )
        assertNull(toSyncedAnnouncementEntity(own, accountId = 1))

        val schoolForStudent = toSyncedAnnouncementEntity(own, accountId = 9)
        assertEquals("Pengumuman: Info Admin", schoolForStudent?.title)
        assertEquals("10", schoolForStudent?.referenceId)

        val classForStudent = toSyncedAnnouncementEntity(
            AnnouncementDto(
                id = 11,
                title = "Tugas Gambar",
                content = "Bawa krayon.",
                scope = "CLASS",
                classGroupId = 3,
                classGroup = IdNameDto(3, "X-1"),
                author = AnnouncementAuthorDto(id = 1, name = "Admin", role = "ADMIN"),
                date = "2026-11-19",
                createdAt = "2026-11-19T02:00:00.000Z"
            ),
            accountId = 9
        )
        assertEquals("Pengumuman X-1: Tugas Gambar", classForStudent?.title)
        assertEquals("ANNOUNCEMENT", classForStudent?.type)
        assertEquals(java.time.Instant.parse("2026-11-19T02:00:00.000Z").toEpochMilli(), classForStudent?.receivedAt)
    }
}
