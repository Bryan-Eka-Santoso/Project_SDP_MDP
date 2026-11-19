package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.NotificationEntity
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AnnouncementDto
import id.sch.santaagnes.sim.data.remote.SchoolInfoApi
import id.sch.santaagnes.sim.domain.model.AppNotification
import id.sch.santaagnes.sim.domain.model.NotificationType
import id.sch.santaagnes.sim.domain.model.PushPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Riwayat notifikasi akun yang sedang login di Room (ADR-010, F-NTF-01), plus sinkronisasi pengumuman. */
interface NotificationRepository {

    /** Terbaru di atas. Kosong bila belum ada sesi. */
    fun observe(): Flow<List<AppNotification>>

    fun observeUnreadCount(): Flow<Int>

    /** Menyimpan pesan masuk; null bila tidak ada akun yang login atau duplikat pengumuman. */
    suspend fun save(payload: PushPayload): AppNotification?

    /** Mengambil pengumuman terbaru milik user agar tetap tampil di tab Notifikasi walau login bergantian/tanpa FCM. */
    suspend fun syncAnnouncements() {}

    suspend fun markRead(id: Long)

    suspend fun markAllRead()
}

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: LocalDao,
    private val session: SessionManager,
    private val schoolInfoApi: SchoolInfoApi
) : NotificationRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<AppNotification>> =
        session.userId.flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(emptyList())
            } else {
                dao.getNotifications(accountId).map { rows -> rows.map { it.toDomain() } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnreadCount(): Flow<Int> =
        session.userId.flatMapLatest { accountId ->
            if (accountId == null) {
                flowOf(0)
            } else {
                dao.observeUnreadNotificationCount(accountId)
            }
        }

    override suspend fun save(payload: PushPayload): AppNotification? {
        val accountId = session.getUserId() ?: return null
        if (payload.type == NotificationType.ANNOUNCEMENT && payload.referenceId != null) {
            if (dao.hasNotification(accountId, NotificationType.ANNOUNCEMENT.name, payload.referenceId)) {
                return null
            }
        }
        val entity = NotificationEntity(
            accountId = accountId,
            title = payload.title,
            message = payload.body,
            type = payload.type.name,
            referenceId = payload.referenceId
        )
        return entity.copy(id = dao.saveNotification(entity)).toDomain()
    }

    override suspend fun syncAnnouncements() {
        val accountId = session.getUserId() ?: return
        val items = runCatching {
            schoolInfoApi.getAnnouncements(page = 1, limit = SYNC_LIMIT).data.orEmpty()
        }.getOrNull() ?: return

        items.forEach { dto ->
            val refId = dto.id.toString()
            if (dao.hasNotification(accountId, NotificationType.ANNOUNCEMENT.name, refId)) {
                return@forEach
            }
            val entity = toSyncedAnnouncementEntity(dto, accountId) ?: return@forEach
            dao.saveNotification(entity)
        }
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

    private companion object {
        const val SYNC_LIMIT = 20
    }
}

internal fun toSyncedAnnouncementEntity(dto: AnnouncementDto, accountId: Int): NotificationEntity? {
    // Pengumuman yang dibuat oleh akun ini sendiri tidak dikirim balik sebagai notifikasi.
    if (dto.author?.id == accountId) return null
    val title = if (dto.scope == "SCHOOL") {
        "Pengumuman: ${dto.title}"
    } else {
        "Pengumuman ${dto.classGroup?.name ?: "Kelas"}: ${dto.title}"
    }
    val receivedAt = dto.createdAt?.let { raw ->
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    } ?: runCatching {
        LocalDate.parse(dto.date).atStartOfDay(DateTimeWib.zone).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }

    return NotificationEntity(
        accountId = accountId,
        title = title,
        message = dto.content,
        type = NotificationType.ANNOUNCEMENT.name,
        referenceId = dto.id.toString(),
        receivedAt = receivedAt
    )
}
