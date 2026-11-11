
package id.sch.santaagnes.sim.data.local

import androidx.room.Entity

@Entity(
    tableName = "schedules",
    primaryKeys = ["accountId", "scheduleId"]
)
data class ScheduleEntity(
    val accountId: Int,
    val scheduleId: Int,
    val subjectName: String,
    val teacherName: String,
    val className: String,
    val roomName: String?,
    val day: String,
    val startTime: String,
    val endTime: String
)
