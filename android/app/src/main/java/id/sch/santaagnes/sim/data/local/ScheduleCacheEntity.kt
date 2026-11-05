
package id.sch.santaagnes.sim.data.local

import androidx.room.Entity

@Entity(
    tableName = "schedule_cache",
    primaryKeys = ["studentId", "scheduleId"]
)
data class ScheduleCacheEntity(
    val studentId: String,
    val scheduleId: String,
    val subjectName: String,
    val teacherName: String,
    val day: String,
    val startTime: String,
    val endTime: String
)
