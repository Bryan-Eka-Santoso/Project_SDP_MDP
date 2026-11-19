package id.sch.santaagnes.sim.domain.model

data class AuthUser(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    // NIS (siswa) atau NIP (guru); null untuk role lain
    val identifier: String? = null,
    val isHomeroomTeacher: Boolean,
    // id siswa (bukan id akun); hanya terisi untuk role STUDENT
    val studentId: Int? = null,
    // kelas binaan wali kelas (tahun ajaran aktif); kosong bila bukan wali kelas
    val homeroomClassGroups: List<NamedId> = emptyList(),
    // anak-anak wali murid; kosong untuk role lain
    val children: List<NamedId> = emptyList(),
    // kelas siswa pada tahun ajaran aktif dan wali kelasnya; kosong untuk role lain
    val classNames: List<String> = emptyList(),
    val homeroomTeacherNames: List<String> = emptyList(),
    // nomor telepon siswa atau wali murid; null bila tidak diisi
    val phone: String? = null
)
