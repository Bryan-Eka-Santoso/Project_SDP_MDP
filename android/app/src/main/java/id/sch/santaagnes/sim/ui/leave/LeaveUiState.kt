package id.sch.santaagnes.sim.ui.leave

import id.sch.santaagnes.sim.data.remote.LeaveChildDto
import id.sch.santaagnes.sim.data.remote.LeaveRequestDto

data class LeaveUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val children: List<LeaveChildDto> = emptyList(),
    val requests: List<LeaveRequestDto> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)
