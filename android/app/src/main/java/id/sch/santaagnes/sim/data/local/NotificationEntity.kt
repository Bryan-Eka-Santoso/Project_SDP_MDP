
package id.sch.santaagnes.sim.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Int,
    val title: String,
    val message: String,
    val type: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
