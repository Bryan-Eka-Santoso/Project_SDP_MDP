package id.sch.santaagnes.sim.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Riwayat notifikasi di perangkat (ADR-010): tidak ada salinan di server. */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Int,
    val title: String,
    val message: String,
    val type: String,
    // id data asal (mis. id perubahan jadwal), dikirim server sebagai teks; null bila tidak ada
    val referenceId: String? = null,
    val receivedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
