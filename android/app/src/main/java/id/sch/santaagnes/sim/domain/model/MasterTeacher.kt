package id.sch.santaagnes.sim.domain.model

/** Data guru pada layar Master Guru (F-MST-05). */
data class MasterTeacher(
    val id: Int,
    val userId: Int,
    val employeeNumber: String,
    val name: String,
    val email: String,
    val status: String,
    val isActive: Boolean = status == "ACTIVE",
    val isHomeroomTeacher: Boolean = false
)

/** Rincian penugasan dan kelas binaan satu guru. */
data class MasterTeacherDetail(
    val teacher: MasterTeacher,
    val homeroomClasses: List<String> = emptyList(),
    val taughtSubjectsSummary: List<String> = emptyList()
)

/** Penugasan guru mengajar per mata pelajaran dan kelas (F-MST-10). */
data class MasterTeachingAssignment(
    val id: Int,
    val teacherId: Int,
    val teacherName: String,
    val teacherNip: String,
    val subjectId: Int,
    val subjectCode: String,
    val subjectName: String,
    val subjectType: String,
    val classGroupId: Int,
    val className: String,
    val academicYearName: String?,
    val semesterId: Int,
    val semesterType: String,
    val usageCount: Int = 0
)

data class TeacherFilter(
    val search: String? = null,
    val isHomeroomTeacher: Boolean? = null
)
