
package id.sch.santaagnes.sim.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScheduleCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao
}
