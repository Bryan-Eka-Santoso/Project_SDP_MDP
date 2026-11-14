package id.sch.santaagnes.sim.ui.grades

import id.sch.santaagnes.sim.domain.model.GradeAssignment

data class TeacherClassesUiState(
    val loading: Boolean = false,
    val assignments: List<GradeAssignment> = emptyList(),
    val error: String? = null,
)
