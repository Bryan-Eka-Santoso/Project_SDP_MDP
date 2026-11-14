package id.sch.santaagnes.sim.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Pengingat tugas pribadi siswa (F-JDW-07). Hanya ada di perangkat, tidak dikirim ke server. */
@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Int,
    val title: String,
    val notes: String? = null,
    // YYYY-MM-DD (WIB); teks ISO sehingga urutan teks sama dengan urutan tanggal
    val dueDate: String,
    val subjectId: Int? = null,
    // nama mapel disalin agar tetap tampil walau jadwal di cache sudah berganti
    val subjectName: String? = null,
    val isCompleted: Boolean = false
)
