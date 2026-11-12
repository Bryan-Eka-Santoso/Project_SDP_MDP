package id.sch.santaagnes.sim.ui.grades

import id.sch.santaagnes.sim.domain.model.GradeAssignment
import id.sch.santaagnes.sim.domain.model.GradeComponent
import id.sch.santaagnes.sim.domain.model.GradeScore
import id.sch.santaagnes.sim.domain.model.GradeStudent

data class GradeManageUiState(
    val isLoading: Boolean = false,
    val assignments: List<GradeAssignment> = emptyList(),
    val selectedAssignmentId: Int? = null,
    val students: List<GradeStudent> = emptyList(),
    val components: List<GradeComponent> = emptyList(),
    val scores: List<GradeScore> = emptyList(),
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val totalWeight: Int get() = components.sumOf { it.weight }
}
