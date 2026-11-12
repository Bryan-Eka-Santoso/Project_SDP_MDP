package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.NotificationEntity
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.domain.model.AppNotification
import id.sch.santaagnes.sim.domain.model.NotificationType
import id.sch.santaagnes.sim.domain.model.PushPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Riwayat notifikasi akun yang sedang login, hanya di Room (ADR-010, F-NTF-01). */
interface NotificationRepository {

    /** Terbaru di atas. Kosong bila belum ada sesi. */
    fun observe(): Flow<List<AppNotification>>

    fun observeUnreadCount(): Flow<Int>

    /** Menyimpan pesan masuk; null bila tidak ada akun yang login (pesan diabaikan). */
    suspend fun save(payload: PushPayload): AppNotification?

    suspend fun markRead(id: Long)

    suspend fun markAllRead()
}

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: LocalDao,
    private val session: SessionManager
) : NotificationRepository {

    override fun observe(): Flow<List<AppNotification>> = flow {
        val accountId = session.getUserId() ?: return@flow
        emitAll(dao.getNotifications(accountId).map { rows -> rows.map { it.toDomain() } })
    }

    override fun observeUnreadCount(): Flow<Int> = flow {
        val accountId = session.getUserId() ?: return@flow
        emitAll(dao.observeUnreadNotificationCount(accountId))
    }

    override suspend fun save(payload: PushPayload): AppNotification? {
        val accountId = session.getUserId() ?: return null
        val entity = NotificationEntity(
            accountId = accountId,
            title = payload.title,
            message = payload.body,
            type = payload.type.name,
            referenceId = payload.referenceId
        )
        return entity.copy(id = dao.saveNotification(entity)).toDomain()
    }

    override suspend fun markRead(id: Long) {
        val accountId = session.getUserId() ?: return
        dao.markNotificationRead(id, accountId)
    }

    override suspend fun markAllRead() {
        val accountId = session.getUserId() ?: return
        dao.markAllNotificationsRead(accountId)
    }

    private fun NotificationEntity.toDomain() = AppNotification(
        id = id,
        type = NotificationType.fromWire(type),
        title = title,
        message = message,
        referenceId = referenceId,
        receivedAtMillis = receivedAt,
        isRead = isRead
    )
}
