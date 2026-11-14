package id.sch.santaagnes.sim.notification

import id.sch.santaagnes.sim.data.repository.NotificationRepository
import id.sch.santaagnes.sim.domain.model.AppNotification
import id.sch.santaagnes.sim.domain.model.NotificationType
import id.sch.santaagnes.sim.domain.model.PushPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNotificationRepository : NotificationRepository {
    // false = tidak ada akun yang login
    var loggedIn = true
    val items = MutableStateFlow<List<AppNotification>>(emptyList())
    private var nextId = 1L

    override fun observe(): Flow<List<AppNotification>> = items

    override fun observeUnreadCount(): Flow<Int> = items.map { list -> list.count { !it.isRead } }

    override suspend fun save(payload: PushPayload): AppNotification? {
        if (!loggedIn) return null
        val saved = AppNotification(
            id = nextId++,
            type = payload.type,
            title = payload.title,
            message = payload.body,
            referenceId = payload.referenceId,
            receivedAtMillis = 0L,
            isRead = false
        )
        items.value = listOf(saved) + items.value
        return saved
    }

    override suspend fun markRead(id: Long) {
        items.value = items.value.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    override suspend fun markAllRead() {
        items.value = items.value.map { it.copy(isRead = true) }
    }
}

class PushMessageTest {

    private val data = mapOf(
        "type" to "SCHEDULE_CHANGE",
        "title" to "Perubahan jadwal",
        "body" to "Matematika X-1 diganti guru.",
        "referenceId" to "12",
        "sentAt" to "2026-11-12T01:00:00.000Z"
    )

    @Test
    fun parsesServerPayload() {
        val payload = PushPayload.from(data)

        assertEquals(NotificationType.SCHEDULE_CHANGE, payload?.type)
        assertEquals("Perubahan jadwal", payload?.title)
        assertEquals("Matematika X-1 diganti guru.", payload?.body)
        assertEquals("12", payload?.referenceId)
    }

    @Test
    fun emptyReferenceBecomesNull() {
        assertNull(PushPayload.from(data + ("referenceId" to ""))?.referenceId)
    }

    @Test
    fun unknownTypeIsKeptAsOther() {
        assertEquals(NotificationType.OTHER, PushPayload.from(data + ("type" to "SOMETHING_NEW"))?.type)
    }

    @Test
    fun messageWithoutTitleOrBodyIsIgnored() {
        assertNull(PushPayload.from(data - "title"))
        assertNull(PushPayload.from(data + ("body" to "  ")))
        assertNull(PushPayload.from(emptyMap()))
    }

    @Test
    fun everyBackendTypeHasItsOwnChannelGroup() {
        assertEquals(NotificationChannelSpec.SCHEDULE, NotificationChannelSpec.forType(NotificationType.SCHEDULE_CHANGE))
        assertEquals(NotificationChannelSpec.SCHEDULE, NotificationChannelSpec.forType(NotificationType.TASK))
        assertEquals(NotificationChannelSpec.ANNOUNCEMENT, NotificationChannelSpec.forType(NotificationType.ANNOUNCEMENT))
        assertEquals(NotificationChannelSpec.ACADEMIC, NotificationChannelSpec.forType(NotificationType.VIOLATION))
        assertEquals(NotificationChannelSpec.ACADEMIC, NotificationChannelSpec.forType(NotificationType.LEAVE_REQUEST))
        assertEquals(NotificationChannelSpec.BOOKING, NotificationChannelSpec.forType(NotificationType.BOOKING))
        assertEquals(NotificationChannelSpec.BILL, NotificationChannelSpec.forType(NotificationType.BILL_OVERDUE))
        assertEquals(NotificationChannelSpec.GENERAL, NotificationChannelSpec.forType(NotificationType.OTHER))
    }

    @Test
    fun handlerSavesThenShows() = runTest {
        val repository = FakeNotificationRepository()
        val shown = mutableListOf<AppNotification>()
        val handler = PushMessageHandler(repository, object : NotificationPresenter {
            override fun show(notification: AppNotification) {
                shown += notification
            }
        })

        val handled = handler.handle(data)

        assertTrue(handled)
        assertEquals(1, repository.items.value.size)
        assertEquals(repository.items.value, shown)
    }

    @Test
    fun handlerIgnoresMessageWhenNobodyIsLoggedIn() = runTest {
        val repository = FakeNotificationRepository().apply { loggedIn = false }
        var shownCount = 0
        val handler = PushMessageHandler(repository, object : NotificationPresenter {
            override fun show(notification: AppNotification) {
                shownCount++
            }
        })

        assertFalse(handler.handle(data))
        assertEquals(0, shownCount)
        assertTrue(repository.items.value.isEmpty())
    }

    @Test
    fun handlerIgnoresForeignMessage() = runTest {
        val repository = FakeNotificationRepository()
        val handler = PushMessageHandler(repository, object : NotificationPresenter {
            override fun show(notification: AppNotification) = error("tidak boleh tampil")
        })

        assertFalse(handler.handle(mapOf("foo" to "bar")))
        assertTrue(repository.items.value.isEmpty())
    }
}
