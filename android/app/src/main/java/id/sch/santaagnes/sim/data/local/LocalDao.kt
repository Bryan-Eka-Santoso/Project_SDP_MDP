
package id.sch.santaagnes.sim.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Hasil kueri mapel unik pada jadwal tersimpan. */
data class SubjectRow(
    val subjectId: Int,
    val subjectName: String
)

@Dao
interface LocalDao {

    // =========================
    // JADWAL
    // =========================

    // Tanggal disimpan sebagai YYYY-MM-DD sehingga urutan teks sama dengan urutan tanggal.
    @Query(
        "SELECT * FROM schedules WHERE accountId = :accountId AND scope = :scope " +
            "AND date BETWEEN :from AND :to ORDER BY date, startTime, slotId"
    )
    fun observeSchedules(accountId: Int, scope: Int, from: String, to: String): Flow<List<ScheduleEntity>>

    @Query(
        "DELETE FROM schedules WHERE accountId = :accountId AND scope = :scope " +
            "AND date BETWEEN :from AND :to"
    )
    suspend fun deleteSchedulesInRange(accountId: Int, scope: Int, from: String, to: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(items: List<ScheduleEntity>)


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

    @Query("UPDATE notifications SET isRead = 1 WHERE accountId = :accountId AND isRead = 0")
    suspend fun markAllNotificationsRead(accountId: Int)

    @Query("SELECT COUNT(*) FROM notifications WHERE accountId = :accountId AND isRead = 0")
    fun observeUnreadNotificationCount(accountId: Int): Flow<Int>


    // =========================
    // TO-DO
    // =========================

    // Yang belum selesai di atas, tenggat terdekat lebih dulu.
    @Query("SELECT * FROM todo_items WHERE accountId = :accountId ORDER BY isCompleted, dueDate, id")
    fun getTodos(accountId: Int): Flow<List<TodoItem>>

    @Insert
    suspend fun addTodo(item: TodoItem): Long

    @Query("UPDATE todo_items SET isCompleted = :completed WHERE id = :id AND accountId = :accountId")
    suspend fun updateTodo(id: Long, accountId: Int, completed: Boolean)

    @Query("DELETE FROM todo_items WHERE id = :id AND accountId = :accountId")
    suspend fun deleteTodo(id: Long, accountId: Int)


    // =========================
    // TUGAS DARI GURU (tanda selesai lokal)
    // =========================

    @Query("SELECT taskId FROM task_done WHERE accountId = :accountId")
    fun observeDoneTaskIds(accountId: Int): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markTaskDone(item: TaskDoneEntity)

    @Query("DELETE FROM task_done WHERE accountId = :accountId AND taskId = :taskId")
    suspend fun unmarkTaskDone(accountId: Int, taskId: Int)


    // =========================
    // PILIHAN MAPEL (dari jadwal yang sudah tersimpan)
    // =========================

    @Query(
        "SELECT DISTINCT subjectId, subjectName FROM schedules " +
            "WHERE accountId = :accountId AND scope = :scope ORDER BY subjectName"
    )
    fun observeSubjects(accountId: Int, scope: Int): Flow<List<SubjectRow>>
}
