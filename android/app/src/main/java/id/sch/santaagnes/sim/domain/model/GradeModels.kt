package id.sch.santaagnes.sim.domain.model

data class GradeAssignment(val id: Int, val label: String)
data class GradeStudent(val id: Int, val name: String, val nis: String?)
data class GradeComponent(val id: Int, val name: String, val weight: Int)
data class GradeScore(val studentId: Int, val componentId: Int, val value: Double)
