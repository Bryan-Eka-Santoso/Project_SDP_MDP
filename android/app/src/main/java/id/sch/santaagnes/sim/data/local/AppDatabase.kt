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
    // v6: penanda notifikasi hasil sinkron (MIGRATION_5_6).
    version = 6,
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

/** Pernyataan SQL v6; baris hasil sinkron versi lama diubah ke format yang cocok dengan push. */
private val MIGRATION_5_6_SQL = listOf(
    "ALTER TABLE notifications ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0",
    // Tunggakan lama dibuat per tagihan; sinkron berikutnya membuat satu ringkasan per siswa.
    "DELETE FROM notifications WHERE type = 'BILL_OVERDUE' AND title = 'Tagihan Menunggak'",
    "UPDATE notifications SET isSynced = 1, title = 'Perubahan jadwal' " +
        "WHERE type = 'SCHEDULE_CHANGE' AND title = 'Perubahan Jadwal'",
    "UPDATE notifications SET isSynced = 1, title = 'Tugas baru' " +
        "WHERE type = 'TASK' AND title LIKE 'Tugas Baru: %'",
    "UPDATE notifications SET isSynced = 1, referenceId = substr(referenceId, 9) " +
        "WHERE referenceId LIKE 'pending:%'",
    "UPDATE notifications SET title = 'Pengajuan Izin Baru' " +
        "WHERE type = 'LEAVE_REQUEST' AND title = 'Pengajuan Izin Menunggu'",
    "UPDATE notifications SET isSynced = 1, " +
        "referenceId = substr(referenceId, 1, instr(referenceId, ':') - 1) " +
        "WHERE type IN ('BOOKING', 'LEAVE_REQUEST') AND instr(referenceId, ':') > 0",
    "UPDATE notifications SET title = 'Pengembalian Fasilitas Dikonfirmasi' " +
        "WHERE type = 'BOOKING' AND title = 'Pengembalian Dikonfirmasi'",
    // Versi lama memakai tanggal kejadian sebagai waktu, sehingga yang masih akan datang menumpuk di atas.
    "UPDATE notifications SET receivedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000 " +
        "WHERE receivedAt > CAST(strftime('%s', 'now') AS INTEGER) * 1000"
)

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_5_6_SQL.forEach(db::execSQL)
    }
}
