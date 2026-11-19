package id.sch.santaagnes.sim.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    // v5: id perubahan + pindah lintas hari pada jadwal (MIGRATION_4_5, data lokal tidak dihapus).
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localDao(): LocalDao
}

/** Migrasi sungguhan supaya riwayat notifikasi, to-do, dan tanda selesai tugas di perangkat tidak hilang. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedules ADD COLUMN changeId INTEGER")
        db.execSQL("ALTER TABLE schedules ADD COLUMN changeMovedTo TEXT")
        db.execSQL("ALTER TABLE schedules ADD COLUMN changeMovedFrom TEXT")
    }
}
