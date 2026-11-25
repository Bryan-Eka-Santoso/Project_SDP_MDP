package id.sch.santaagnes.sim.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Satu halaman hasil daftar berpaginasi; `hasMore` true bila masih ada halaman berikutnya. */
data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean,
    // jumlah seluruh data di server (meta.total), untuk baris "Total: N"; null bila tidak diketahui
    val total: Int? = null,
    // jumlah murid di kelas (hanya daftar tagihan wali kelas)
    val studentCount: Int? = null
)

/** Slot jadwal mingguan pada master jadwal. */
data class AdminSlot(
    val id: Int,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val roomId: Int?,
    val roomName: String?,
    val assignmentId: Int,
    val teacherId: Int,
    val teacherName: String,
    val subjectName: String,
    val classGroupId: Int,
    val classGroupName: String
)

data class SlotInput(
    val assignmentId: Int,
    val roomId: Int,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/** Filter daftar slot; null = tidak difilter. */
data class SlotFilter(
    val dayOfWeek: DayOfWeek? = null,
    val classGroupId: Int? = null,
    val teacherId: Int? = null
)

/** Perubahan jadwal pada satu tanggal. */
data class AdminChange(
    val id: Int,
    val date: LocalDate,
    val type: ChangeType,
    val description: String,
    val substituteTeacherId: Int?,
    val substituteTeacherName: String?,
    val newRoomId: Int?,
    val newRoomName: String?,
    val newStartTime: LocalTime?,
    val newEndTime: LocalTime?,
    val slot: AdminSlot,
    // Tanggal tujuan bila pertemuan dipindah ke hari lain.
    val newDate: LocalDate? = null
)

/** Isi formulir perubahan; kolom yang tidak dipakai jenisnya dibiarkan null. */
data class ChangeInput(
    val slotId: Int,
    val date: LocalDate,
    val type: ChangeType,
    val description: String,
    val substituteTeacherId: Int? = null,
    val newRoomId: Int? = null,
    val newStartTime: LocalTime? = null,
    val newEndTime: LocalTime? = null,
    val newDate: LocalDate? = null
)

/** Pilihan penugasan (kelas + mapel + guru) pada formulir slot. */
data class AssignmentOption(
    val id: Int,
    val classGroupId: Int,
    val classGroupName: String,
    val subjectName: String,
    val teacherId: Int,
    val teacherName: String
)

data class RoomOption(
    val id: Int,
    val name: String
)

data class TeacherOption(
    val id: Int,
    val name: String
)

/** Data pilihan untuk formulir admin. Guru dan ruangan diambil dari master ditambah penugasan yang ada. */
data class ScheduleLookups(
    val assignments: List<AssignmentOption>,
    val rooms: List<RoomOption>,
    val allTeachers: List<TeacherOption> = emptyList()
) {
    val teachers: List<TeacherOption>
        get() = (allTeachers + assignments.map { TeacherOption(it.teacherId, it.teacherName) })
            .distinctBy { it.id }
            .sortedBy { it.name }

    val classGroups: List<NamedId>
        get() = assignments.map { NamedId(it.classGroupId, it.classGroupName) }
            .distinctBy { it.id }
            .sortedBy { it.name }
}

data class ImportResult(
    val imported: Int
)
