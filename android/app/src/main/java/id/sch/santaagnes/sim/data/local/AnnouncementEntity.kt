
package id.sch.santaagnes.sim.data.local

import androidx.room.Entity

@Entity(
    tableName = "announcements",
    primaryKeys = ["accountId", "announcementId"]
)
data class AnnouncementEntity(
    val accountId: Int,
    val announcementId: Int,
    val title: String,
    val content: String,
    val publishedAt: String,
    val attachmentUrl: String? = null
)
