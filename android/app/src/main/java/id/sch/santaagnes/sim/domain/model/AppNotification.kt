package id.sch.santaagnes.sim.domain.model

/** Jenis notifikasi; nilainya sama dengan `NOTIFICATION_TYPES` di backend. */
enum class NotificationType {
    SCHEDULE_CHANGE,
    ANNOUNCEMENT,
    VIOLATION,
    LEAVE_REQUEST,
    BOOKING,
    BILL_OVERDUE,
    TASK,

    // jenis yang belum dikenal aplikasi ini (server lebih baru): tetap ditampilkan
    OTHER;

    companion object {
        fun fromWire(value: String?): NotificationType =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/** Satu entri riwayat notifikasi di perangkat. */
data class AppNotification(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val message: String,
    val referenceId: String?,
    val receivedAtMillis: Long,
    val isRead: Boolean
)

/** Isi pesan FCM (data message) setelah divalidasi. */
data class PushPayload(
    val type: NotificationType,
    val title: String,
    val body: String,
    val referenceId: String?
) {
    companion object {
        /** Null bila pesan bukan notifikasi aplikasi (tanpa judul atau isi). */
        fun from(data: Map<String, String>): PushPayload? {
            val title = data["title"]?.trim().orEmpty()
            val body = data["body"]?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) return null
            return PushPayload(
                type = NotificationType.fromWire(data["type"]),
                title = title,
                body = body,
                // server mengirim string kosong bila tidak ada referensi
                referenceId = data["referenceId"]?.takeIf { it.isNotBlank() }
            )
        }
    }
}
