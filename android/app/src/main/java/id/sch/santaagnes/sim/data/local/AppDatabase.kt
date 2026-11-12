package id.sch.santaagnes.sim.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScheduleEntity::class,
        AnnouncementEntity::class,
        NotificationEntity::class,
        TodoItem::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localDao(): LocalDao
}
