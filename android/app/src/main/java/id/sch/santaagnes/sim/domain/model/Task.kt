package id.sch.santaagnes.sim.domain.model

import java.time.LocalDate

/** Tugas dari guru untuk satu kelas + mapel. */
data class Task(
    val id: Int,
    val title: String,
    val description: String?,
    val dueDate: LocalDate,
    val subjectName: String,
    val classGroupName: String,
    val teacherName: String?
)

/** Satu halaman daftar tugas; `hasMore` true bila masih ada halaman berikutnya di server. */
data class TaskPage(
    val tasks: List<Task>,
    val page: Int,
    val hasMore: Boolean
)

/** Kelas + mapel yang diajar guru (semester aktif); `id` dipakai saat membuat tugas. */
data class TeachingAssignment(
    val id: Int,
    val subjectName: String,
    val classGroupName: String
)

/** Isi formulir tugas. `description` kosong berarti tanpa catatan. */
data class TaskInput(
    val title: String,
    val description: String,
    val dueDate: LocalDate
)
