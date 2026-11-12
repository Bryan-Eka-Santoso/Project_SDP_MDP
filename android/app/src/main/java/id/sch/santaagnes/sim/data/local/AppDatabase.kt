package id.sch.santaagnes.sim.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScheduleEntity::class,
        AnnouncementEntity::class,
        NotificationEntity::class,
        TodoItem::class,
        TaskDoneEntity::class
    ],
    // v2: ScheduleEntity per tanggal. v3: subjectId pada jadwal, TodoItem diperluas, TaskDoneEntity baru.
    // Tabel lokal-saja (notifikasi, to-do, tanda selesai tugas) butuh Migration sungguhan begitu berisi
    // data pengguna; sampai itu, DatabaseModule memakai fallback destruktif.
    // v4: referenceId pada notifikasi.
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localDao(): LocalDao
}
