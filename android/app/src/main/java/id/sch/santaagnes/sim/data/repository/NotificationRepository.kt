package id.sch.santaagnes.sim.data.repository

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.NotificationEntity
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AnnouncementDto
import id.sch.santaagnes.sim.data.remote.AuthApi
import id.sch.santaagnes.sim.data.remote.BillApi
import id.sch.santaagnes.sim.data.remote.LeaveApi
import id.sch.santaagnes.sim.data.remote.PeminjamanApi
import id.sch.santaagnes.sim.data.remote.RiwayatPelanggaranApi
import id.sch.santaagnes.sim.data.remote.RiwayatPeminjamanApi
import id.sch.santaagnes.sim.data.remote.ScheduleApi
import id.sch.santaagnes.sim.data.remote.SchoolInfoApi
import id.sch.santaagnes.sim.data.remote.TaskApi
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

/** Riwayat notifikasi akun yang sedang login di Room (ADR-010, F-NTF-01), plus sinkronisasi dari API. */
interface NotificationRepository {

    /** Terbaru di atas. Kosong bila belum ada sesi. */
    fun observe(): Flow<List<AppNotification>>

    fun observeUnreadCount(): Flow<Int>

    /** Menyimpan pesan masuk; null bila tidak ada akun yang login atau pesan yang sama dikirim ulang. */
    suspend fun save(payload: PushPayload): AppNotification?

    /** Menyinkronkan kejadian terbaru milik akun (pengumuman, jadwal, tugas, izin, peminjaman, pelanggaran, tunggakan) ke Room. */
    suspend fun syncAnnouncements() {}

    suspend fun markRead(id: Long)

    suspend fun markAllRead()
}

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: LocalDao,
    private val session: SessionManager,
    private val authApi: AuthApi,
    private val schoolInfoApi: SchoolInfoApi,
    private val scheduleApi: ScheduleApi,
    private val taskApi: TaskApi,
    private val billApi: BillApi,
    private val leaveApi: LeaveApi,
    private val violationApi: RiwayatPelanggaranApi,
    private val bookingHistoryApi: RiwayatPeminjamanApi,
    private val adminBookingApi: PeminjamanApi
) : NotificationRepository {

    // Push (thread FCM) dan sinkron bisa berjalan bersamaan: periksa-lalu-tulis harus berurutan.
    private val writeLock = Mutex()

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
        val entity = NotificationEntity(
            accountId = accountId,
            title = payload.title,
            message = payload.body,
            type = payload.type.name,
            referenceId = payload.referenceId,
            receivedAt = payload.sentAtMillis ?: System.currentTimeMillis()
        )
        return write(entity, ::mergePush)?.toDomain()
    }

    /** Baris yang tersimpan, atau null bila tidak ada yang berubah. */
    private suspend fun write(
        entity: NotificationEntity,
        merge: (List<NotificationEntity>, NotificationEntity) -> NotificationWrite
    ): NotificationEntity? = writeLock.withLock {
        val existing = entity.referenceId
            ?.let { dao.findNotifications(entity.accountId, entity.type, it, entity.title) }
            .orEmpty()
        when (val result = merge(existing, entity)) {
            NotificationWrite.Skip -> null
            is NotificationWrite.Insert -> result.entity.copy(id = dao.saveNotification(result.entity))
            is NotificationWrite.Update -> result.entity.also { dao.updateNotification(it) }
        }
    }

    override suspend fun syncAnnouncements() {
        val accountId = session.getUserId() ?: return
        val role = session.getRole() ?: return

        syncAnnouncementItems(accountId)

        when (role) {
            "STUDENT" -> {
                syncScheduleChanges(accountId, studentId = null)
                syncStudentTasks(accountId)
                syncBookingHistory(accountId)
                val studentId = runCatching { authApi.getProfile().data?.student?.id }.getOrNull()
                if (studentId != null) {
                    syncOverdueBills(accountId, studentId, session.getUserName() ?: "Anda")
                }
            }
            "GUARDIAN" -> {
                val children = runCatching { authApi.getProfile().data?.guardian?.children.orEmpty() }
                    .getOrDefault(emptyList())
                children.forEach { child ->
                    syncScheduleChanges(accountId, studentId = child.id)
                    syncOverdueBills(accountId, child.id, child.name)
                    syncChildViolations(accountId, child.id, child.name)
                    syncChildLeaveDecisions(accountId, child.id)
                }
            }
            "TEACHER" -> {
                syncScheduleChanges(accountId, studentId = null)
                syncHomeroomPendingLeaves(accountId)
                syncBookingHistory(accountId)
            }
            "ADMIN" -> {
                syncAdminPendingBookings(accountId)
            }
        }
    }

    private suspend fun saveSynced(entity: NotificationEntity) {
        write(entity.copy(isSynced = true), ::mergeSynced)
    }

    private suspend fun syncAnnouncementItems(accountId: Int) {
        val items = runCatching {
            schoolInfoApi.getAnnouncements(page = 1, limit = SYNC_LIMIT).data.orEmpty()
        }.getOrNull() ?: return

        items.forEach { dto ->
            val entity = toSyncedAnnouncementEntity(dto, accountId) ?: return@forEach
            saveSynced(entity)
        }
    }

    private suspend fun syncScheduleChanges(accountId: Int, studentId: Int?) {
        val today = DateTimeWib.today()
        val entries = runCatching {
            scheduleApi.getSchedule(
                dateFrom = today.toString(),
                dateTo = today.plusDays(SCHEDULE_WINDOW_DAYS).toString(),
                studentId = studentId,
                classGroupId = null
            ).data.orEmpty()
        }.getOrNull() ?: return

        val changed = entries.filter { it.change != null }
            .groupBy { it.change?.id }
            .map { (_, same) -> same.firstOrNull { it.change?.movedTo == null } ?: same.first() }
        changed.forEach { entry ->
            val change = entry.change ?: return@forEach
            val typeLabel = when (change.type) {
                "CANCEL" -> "dibatalkan"
                "SUBSTITUTE" -> "diajar guru pengganti ${entry.teacher.name}"
                else -> "dipindah ke ${entry.startTime}–${entry.endTime}${entry.room?.name?.let { " di $it" }.orEmpty()}"
            }
            val dateLabel = runCatching { DateTimeWib.formatDate(LocalDate.parse(entry.date)) }
                .getOrDefault(entry.date)
            val detail = buildString {
                append("${entry.subject.name} ${entry.classGroup.name}, $dateLabel: $typeLabel.")
                if (!change.description.isNullOrBlank()) append(" ${change.description}")
            }
            saveSynced(
                NotificationEntity(
                    accountId = accountId,
                    title = "Perubahan jadwal",
                    message = detail,
                    type = NotificationType.SCHEDULE_CHANGE.name,
                    referenceId = change.id.toString(),
                    receivedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun syncStudentTasks(accountId: Int) {
        val today = DateTimeWib.today().toString()
        val tasks = runCatching {
            taskApi.getTasks(page = 1, limit = SYNC_LIMIT, dueFrom = today).data.orEmpty()
        }.getOrNull() ?: return

        tasks.forEach { task ->
            val dueText = runCatching { DateTimeWib.formatDate(LocalDate.parse(task.dueDate)) }
                .getOrDefault(task.dueDate)
            val timeSuffix = task.dueTime?.let { " pukul $it WIB" }.orEmpty()
            saveSynced(
                NotificationEntity(
                    accountId = accountId,
                    title = "Tugas baru",
                    message = "${task.teachingAssignment.subject.name}: ${task.title}. Tenggat $dueText$timeSuffix.",
                    type = NotificationType.TASK.name,
                    referenceId = task.id.toString(),
                    receivedAt = parseIsoOrNow(task.createdAt)
                )
            )
        }
    }

    private suspend fun syncOverdueBills(accountId: Int, studentId: Int, studentName: String) {
        val response = runCatching {
            billApi.getStudentBills(
                studentId = studentId,
                page = 1,
                limit = BILL_LIMIT,
                status = "UNPAID",
                overdue = true
            )
        }.getOrNull() ?: return
        val bills = response.data.orEmpty()
        if (bills.isEmpty()) return

        saveSynced(
            NotificationEntity(
                accountId = accountId,
                title = "Tunggakan pembayaran",
                message = overdueSummary(studentName, bills, response.meta?.total ?: bills.size) { date ->
                    runCatching { DateTimeWib.formatDate(LocalDate.parse(date)) }.getOrDefault(date)
                },
                type = NotificationType.BILL_OVERDUE.name,
                referenceId = studentId.toString(),
                receivedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun syncChildViolations(accountId: Int, studentId: Int, childName: String) {
        val history = runCatching { violationApi.getRiwayat(studentId).data }.getOrNull() ?: return
        history.violations.take(SYNC_LIMIT).forEach { v ->
            val dateText = runCatching { DateTimeWib.formatDate(LocalDate.parse(v.date)) }.getOrDefault(v.date)
            saveSynced(
                NotificationEntity(
                    accountId = accountId,
                    title = "Pemberitahuan Pelanggaran Siswa",
                    message = "$childName tercatat melakukan pelanggaran \"${v.name}\" (+${v.points} poin) pada $dateText.",
                    type = NotificationType.VIOLATION.name,
                    referenceId = v.id.toString(),
                    receivedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun syncChildLeaveDecisions(accountId: Int, studentId: Int) {
        val leaves = runCatching { leaveApi.list(studentId = studentId, page = 1).data.orEmpty() }.getOrNull() ?: return
        leaves.filter { it.status == "APPROVED" || it.status == "REJECTED" }.forEach { leave ->
            val approved = leave.status == "APPROVED"
            val range = if (leave.startDate == leave.endDate) leave.startDate else "${leave.startDate} s.d. ${leave.endDate}"
            val reasonNote = leave.rejectionReason?.takeIf { it.isNotBlank() }?.let { " Alasan: $it" }.orEmpty()
            saveSynced(
                NotificationEntity(
                    accountId = accountId,
                    title = if (approved) "Pengajuan Izin Disetujui" else "Pengajuan Izin Ditolak",
                    message = if (approved) {
                        "Pengajuan izin ${leave.studentName} ($range) telah disetujui wali kelas."
                    } else {
                        "Pengajuan izin ${leave.studentName} ($range) ditolak.$reasonNote"
                    },
                    type = NotificationType.LEAVE_REQUEST.name,
                    referenceId = leave.id.toString(),
                    receivedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun syncHomeroomPendingLeaves(accountId: Int) {
        val pending = runCatching {
            leaveApi.pendingHomeroom(page = 1, limit = SYNC_LIMIT).data.orEmpty()
        }.getOrNull() ?: return

        pending.forEach { leave ->
            val range = if (leave.startDate == leave.endDate) leave.startDate else "${leave.startDate} s.d. ${leave.endDate}"
            saveSynced(
                NotificationEntity(
                    accountId = accountId,
                    title = "Pengajuan Izin Baru",
                    message = "${leave.studentName} mengajukan izin ($range): ${leave.reason}",
                    type = NotificationType.LEAVE_REQUEST.name,
                    referenceId = leave.id.toString(),
                    receivedAt = parseIsoOrNow(leave.createdAt)
                )
            )
        }
    }

    private suspend fun syncBookingHistory(accountId: Int) {
        val bookings = runCatching {
            bookingHistoryApi.getRiwayat(page = 1, limit = SYNC_LIMIT).data.orEmpty()
        }.getOrNull() ?: return

        bookings.filter { it.status != "PENDING" }.forEach { b ->
            val (title, body) = when (b.status) {
                "APPROVED" -> "Peminjaman Disetujui" to
                    "Pengajuan peminjaman ${b.facilityName} (${b.bookingDate}, ${b.startTime}–${b.endTime}) telah disetujui."
                "REJECTED" -> {
                    val reason = b.rejectionReason?.takeIf { it.isNotBlank() }?.let { " Alasan: $it" }.orEmpty()
                    "Peminjaman Ditolak" to
                        "Pengajuan peminjaman ${b.facilityName} (${b.bookingDate}) ditolak.$reason"
                }
                else -> "Pengembalian Fasilitas Dikonfirmasi" to
                    "Pengembalian fasilitas ${b.facilityName} (${b.bookingDate}) telah dikonfirmasi."
            }
            saveSynced(
                NotificationEntity(
                    accountId = accountId,
                    title = title,
                    message = body,
                    type = NotificationType.BOOKING.name,
                    referenceId = b.id.toString(),
                    receivedAt = parseIsoOrNow(b.updatedAt)
                )
            )
        }
    }

    private suspend fun syncAdminPendingBookings(accountId: Int) {
        val bookings = runCatching {
            adminBookingApi.getAll(page = 1, limit = SYNC_LIMIT, status = "PENDING").data.orEmpty()
        }.getOrNull() ?: return

        bookings.forEach { b ->
            saveSynced(
                NotificationEntity(
                    accountId = accountId,
                    title = "Pengajuan Peminjaman Baru",
                    message = "${b.requesterName} mengajukan peminjaman ${b.facilityName} (${b.bookingDate}, ${b.startTime}–${b.endTime}).",
                    type = NotificationType.BOOKING.name,
                    referenceId = b.id.toString(),
                    receivedAt = parseIsoOrNow(b.createdAt)
                )
            )
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
        const val BILL_LIMIT = 100
        const val SCHEDULE_WINDOW_DAYS = 14L
    }
}

private fun parseDateMillis(dateIso: String): Long =
    runCatching { LocalDate.parse(dateIso).atStartOfDay(DateTimeWib.zone).toInstant().toEpochMilli() }
        .getOrElse { System.currentTimeMillis() }

// Tanpa waktu dari server, waktu notifikasi = saat aplikasi mengetahuinya, bukan tanggal kejadiannya.
private fun parseIsoOrNow(iso: String?): Long =
    iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun parseIsoOrDateMillis(iso: String?, fallbackDate: String): Long =
    iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: parseDateMillis(fallbackDate)

internal fun toSyncedAnnouncementEntity(dto: AnnouncementDto, accountId: Int): NotificationEntity? {
    // Pengumuman yang dibuat oleh akun ini sendiri tidak dikirim balik sebagai notifikasi.
    if (dto.author?.id == accountId) return null
    val title = if (dto.scope == "SCHOOL") {
        "Pengumuman: ${dto.title}"
    } else {
        "Pengumuman ${dto.classGroup?.name ?: "Kelas"}: ${dto.title}"
    }
    val receivedAt = parseIsoOrDateMillis(dto.createdAt, dto.date)

    return NotificationEntity(
        accountId = accountId,
        title = title,
        message = dto.content,
        type = NotificationType.ANNOUNCEMENT.name,
        referenceId = dto.id.toString(),
        receivedAt = receivedAt,
        isSynced = true
    )
}
