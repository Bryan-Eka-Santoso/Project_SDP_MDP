package id.sch.santaagnes.sim.domain.model

/** Tahun ajaran akademik (F-MST-06). */
data class MasterAcademicYear(
    val id: Int,
    val name: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean,
    val classCount: Int = 0,
    val semesterCount: Int = 0
)

/** Semester dalam tahun ajaran (ODD / EVEN). */
data class MasterSemester(
    val id: Int,
    val academicYearId: Int,
    val academicYearName: String? = null,
    val type: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean,
    val assignmentCount: Int = 0
) {
    val label: String
        get() = if (type == "ODD") "Sem. 1 (Ganjil)" else "Sem. 2 (Genap)"
}

data class MasterAcademicYearDetail(
    val academicYear: MasterAcademicYear,
    val semesters: List<MasterSemester>
)
