
package id.sch.santaagnes.sim.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDao {

    // =========================
    // JADWAL
    // =========================

    @Query("SELECT * FROM schedules WHERE accountId = :accountId ORDER BY day, startTime")
    fun getSchedules(accountId: Int): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSchedules(items: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE accountId = :accountId")
    suspend fun clearSchedules(accountId: Int)


    // =========================
    // PENGUMUMAN
    // =========================

    @Query("SELECT * FROM announcements WHERE accountId = :accountId ORDER BY publishedAt DESC")
    fun getAnnouncements(accountId: Int): Flow<List<AnnouncementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnnouncements(items: List<AnnouncementEntity>)

    @Query("DELETE FROM announcements WHERE accountId = :accountId")
    suspend fun clearAnnouncements(accountId: Int)


    // =========================
    // NOTIFIKASI
    // =========================

    @Query("SELECT * FROM notifications WHERE accountId = :accountId ORDER BY receivedAt DESC")
    fun getNotifications(accountId: Int): Flow<List<NotificationEntity>>

    @Insert
    suspend fun saveNotification(item: NotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id AND accountId = :accountId")
    suspend fun markNotificationRead(id: Long, accountId: Int)


    // =========================
    // TO-DO
    // =========================

    @Query("SELECT * FROM todo_items WHERE accountId = :accountId ORDER BY isCompleted, id DESC")
    fun getTodos(accountId: Int): Flow<List<TodoItem>>

    @Insert
    suspend fun addTodo(item: TodoItem): Long

    @Query("UPDATE todo_items SET isCompleted = :completed WHERE id = :id AND accountId = :accountId")
    suspend fun updateTodo(id: Long, accountId: Int, completed: Boolean)

    @Query("DELETE FROM todo_items WHERE id = :id AND accountId = :accountId")
    suspend fun deleteTodo(id: Long, accountId: Int)
}
