package id.sch.santaagnes.sim.domain.model

data class GradeSchedule(
    val id: Int,
    val day: String,
    val startTime: String,
    val endTime: String
)

data class GradeAssignment(
    val id: Int,
    val label: String,
    val subjectName: String = "",
    val className: String = "",
    val semesterName: String = "",
    val schedules: List<GradeSchedule> = emptyList()
)

data class GradeStudent(val id: Int, val name: String, val nis: String?)
data class GradeComponent(val id: Int, val name: String, val weight: Int)
data class GradeScore(val studentId: Int, val componentId: Int, val value: Double)
