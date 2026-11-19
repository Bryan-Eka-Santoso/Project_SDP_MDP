package id.sch.santaagnes.sim.domain.model

/** Akun wali murid yang dapat dihubungkan ke siswa (F-MST-04). */
data class MasterGuardian(
    val id: Int,
    val userId: Int,
    val name: String,
    val email: String,
    val phone: String,
    val status: String,
    val studentCount: Int = 0
)
