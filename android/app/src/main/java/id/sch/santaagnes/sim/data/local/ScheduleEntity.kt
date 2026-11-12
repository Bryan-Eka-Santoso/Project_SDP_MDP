package id.sch.santaagnes.sim.data.local

import androidx.room.Entity

/**
 * Cache jadwal per tanggal (satu baris = satu pelajaran pada satu tanggal), bukan templat mingguan.
 * `scope` memisahkan data per siswa (id siswa; 0 untuk guru) pada akun yang sama.
 */
@Entity(
    tableName = "schedules",
    primaryKeys = ["accountId", "scope", "date", "slotId"]
)
data class ScheduleEntity(
    val accountId: Int,
    val scope: Int,
    val date: String,
    val slotId: Int,
    val startTime: String,
    val endTime: String,
    val subjectId: Int,
    val subjectCode: String?,
    val subjectName: String,
    val teacherName: String,
    val roomName: String?,
    val classGroupName: String,
    val changeType: String?,
    val changeDescription: String?,
    val originalStartTime: String?,
    val originalEndTime: String?,
    val originalRoomName: String?,
    val originalTeacherName: String?
)
