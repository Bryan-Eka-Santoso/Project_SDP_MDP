package id.sch.santaagnes.sim.domain.model

/** Jumlah presensi satu siswa pada satu mata pelajaran dalam periode yang dipilih. */
data class AttendanceRecapRow(
    val studentId: Int,
    val nis: String,
    val name: String,
    val present: Int,
    val sick: Int,
    val excused: Int,
    val absent: Int
)
