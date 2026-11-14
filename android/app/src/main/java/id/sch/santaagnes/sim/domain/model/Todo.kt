package id.sch.santaagnes.sim.domain.model

import java.time.LocalDate

/** Pengingat tugas pribadi siswa; hanya tersimpan di perangkat. */
data class Todo(
    val id: Long,
    val title: String,
    val notes: String?,
    val dueDate: LocalDate,
    val subjectName: String?,
    val isCompleted: Boolean
)

data class SubjectOption(
    val id: Int,
    val name: String
)

data class TodoInput(
    val title: String,
    val notes: String,
    val dueDate: LocalDate,
    val subject: SubjectOption?
)
