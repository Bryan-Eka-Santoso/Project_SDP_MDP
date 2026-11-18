package id.sch.santaagnes.sim.domain.model

data class MasterStudent(
    val id: Int,
    val userId: Int,
    val name: String,
    val email: String,
    val nis: String,
    val status: String,
    val isActive: Boolean = status == "ACTIVE",
    val birthDate: String? = null,
    val phone: String? = null,
    val className: String? = null,
    val classId: Int? = null,
    val guardianCount: Int = 0
)

data class MasterStudentGuardian(
    val guardianId: Int,
    val relationship: String,
    val name: String,
    val phone: String?,
    val email: String
)

data class MasterStudentDetail(
    val student: MasterStudent,
    val guardians: List<MasterStudentGuardian> = emptyList()
)

data class StudentFilter(
    val search: String? = null,
    val classGroupId: Int? = null
)
