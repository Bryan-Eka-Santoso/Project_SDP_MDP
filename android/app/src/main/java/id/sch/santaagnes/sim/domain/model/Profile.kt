package id.sch.santaagnes.sim.domain.model

/** Profil yang ditampilkan di tab Profil (F-AUTH-03). */
data class Profile(
    val name: String,
    // null selama belum dimuat dari server (email tidak disimpan di sesi lokal)
    val email: String?,
    val role: String,
    // NIS (siswa) atau NIP (guru)
    val identifier: String?,
    val homeroomClasses: List<String> = emptyList(),
    val children: List<String> = emptyList(),
    // Siswa: kelas tahun ajaran aktif dan wali kelasnya (baru terisi setelah dimuat dari server).
    val classNames: List<String> = emptyList(),
    val homeroomTeachers: List<String> = emptyList(),
    val phone: String? = null
) {
    /** Siapa yang punya layar tagihan; backend tetap memeriksa cakupan datanya. */
    val canViewBills: Boolean
        get() = when (role) {
            "STUDENT", "ADMIN" -> true
            "GUARDIAN" -> children.isNotEmpty()
            "TEACHER" -> homeroomClasses.isNotEmpty()
            else -> false
        }

    /** Laporan dan ekspor hanya untuk admin (F-LAP-*); backend tetap memeriksa role. */
    val canViewReports: Boolean
        get() = role == "ADMIN"
}
