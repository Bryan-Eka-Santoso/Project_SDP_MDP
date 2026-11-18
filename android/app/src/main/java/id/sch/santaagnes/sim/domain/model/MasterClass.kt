package id.sch.santaagnes.sim.domain.model

/** Data rombongan belajar / kelas pada Master Kelas (F-MST-07, F-MST-08). */
data class MasterClassGroup(
    val id: Int,
    val name: String,
    val gradeLevel: String,
    val capacity: Int,
    val academicYearId: Int,
    val academicYearName: String,
    val isAcademicYearActive: Boolean,
    val homeroomTeacherId: Int? = null,
    val homeroomTeacherName: String? = null,
    val homeroomTeacherNip: String? = null,
    val memberCount: Int = 0,
    val assignmentCount: Int = 0
)

/** Siswa anggota kelas (F-MST-09). */
data class MasterClassMember(
    val studentId: Int,
    val nis: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val birthDate: String? = null
)

data class MasterClassMembersPage(
    val classId: Int,
    val className: String,
    val capacity: Int,
    val members: List<MasterClassMember>
)

data class ClassFilter(
    val academicYearId: Int? = null,
    val gradeLevel: String? = null,
    val search: String? = null
)
