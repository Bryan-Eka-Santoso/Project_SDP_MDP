package id.sch.santaagnes.sim.ui.grades

import id.sch.santaagnes.sim.domain.model.GradeAssignment
import id.sch.santaagnes.sim.domain.model.GradeSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class TeacherClassCardFormatterTest {

    @Test
    fun `ringkasan menampilkan pesan belum tersedia saat jadwal kosong`() {
        val assignment = GradeAssignment(
            id = 1,
            label = "Matematika · X-1",
            subjectName = "Matematika",
            className = "X-1",
            semesterName = "Ganjil 2026/2027",
            schedules = emptyList()
        )

        assertEquals(
            "Semester Ganjil 2026/2027 · Jadwal belum tersedia",
            TeacherClassCardFormatter.compactSummary(assignment)
        )
    }

    @Test
    fun `ringkasan menghitung total sesi dan menggabungkan nama hari yang sama`() {
        val assignment = GradeAssignment(
            id = 2,
            label = "Fisika · XI-2",
            subjectName = "Fisika",
            className = "XI-2",
            semesterName = "Ganjil",
            schedules = listOf(
                GradeSchedule(1, "Senin", "07:00", "07:45"),
                GradeSchedule(2, "Senin", "07:45", "08:30"),
                GradeSchedule(3, "Rabu", "09:30", "10:15")
            )
        )

        assertEquals(
            "Semester Ganjil · 3 jadwal (Senin, Rabu)",
            TeacherClassCardFormatter.compactSummary(assignment)
        )
    }

    @Test
    fun `ringkasan tidak menampilkan awalan Semester kosong jika semesterName blank`() {
        val assignment = GradeAssignment(
            id = 3,
            label = "Kimia · XII-1",
            subjectName = "Kimia",
            className = "XII-1",
            semesterName = " ",
            schedules = listOf(GradeSchedule(4, "Jumat", "10:00", "11:30"))
        )

        assertEquals(
            "1 jadwal (Jumat)",
            TeacherClassCardFormatter.compactSummary(assignment)
        )
    }

    @Test
    fun `detail jadwal memformat hari dan rentang jam secara lengkap`() {
        val rows = TeacherClassCardFormatter.detailRows(
            listOf(
                GradeSchedule(1, "Senin", "07:00", "07:45"),
                GradeSchedule(2, "Kamis", "13:00", "14:30")
            )
        )

        assertEquals(
            listOf("Senin" to "07:00–07:45", "Kamis" to "13:00–14:30"),
            rows
        )
    }
}
