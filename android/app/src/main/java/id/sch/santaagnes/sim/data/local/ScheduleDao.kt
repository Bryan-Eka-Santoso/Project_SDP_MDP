
package id.sch.santaagnes.sim.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query(
        "SELECT * FROM schedule_cache WHERE studentId = :studentId"
    )
    fun getSchedules(
        studentId: String
    ): Flow<List<ScheduleCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(
        schedules: List<ScheduleCacheEntity>
    )

    @Query("DELETE FROM schedule_cache")
    suspend fun clearSchedules()
}
