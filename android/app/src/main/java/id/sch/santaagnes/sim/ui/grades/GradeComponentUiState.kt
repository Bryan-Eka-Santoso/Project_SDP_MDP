
package id.sch.santaagnes.sim.ui.grades

import id.sch.santaagnes.sim.domain.model.GradeComponent

data class GradeComponentUiState(
    val components: List<GradeComponent> = emptyList(),
    val selectedId: Int? = null,
    val totalWeight: Int = 0,
    val isValid: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null
)
