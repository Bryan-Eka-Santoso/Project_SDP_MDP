package id.sch.santaagnes.sim.domain.model

/** Jadwal siapa yang dilihat (menentukan parameter API dan kunci cache). */
sealed interface ScheduleScope {
    /** Milik sendiri: kelas siswa, atau jadwal mengajar guru. */
    data object Own : ScheduleScope

    /** Anak yang dipilih wali murid. */
    data class Child(val studentId: Int) : ScheduleScope

    /** Kelas binaan wali kelas. */
    data class HomeroomClass(val classGroupId: Int) : ScheduleScope
}

/** Satu pilihan pada pemilih jadwal; `name` = nama anak/kelas, null untuk jadwal sendiri. */
data class ScopeOption(
    val scope: ScheduleScope,
    val name: String?
)

/** Siapa yang melihat dan pilihan jadwal yang tersedia baginya. */
data class ScheduleViewer(
    val role: String,
    val options: List<ScopeOption>,
    val initialIndex: Int = 0
)
