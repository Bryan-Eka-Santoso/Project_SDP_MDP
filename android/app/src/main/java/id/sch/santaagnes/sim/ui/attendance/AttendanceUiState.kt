package id.sch.santaagnes.sim.ui.attendance

import id.sch.santaagnes.sim.data.remote.AttendanceSessionDto
import id.sch.santaagnes.sim.data.remote.AttendanceSlotDto

data class AttendanceUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val slots: List<AttendanceSlotDto> = emptyList(),
    val session: AttendanceSessionDto? = null,
    val message: String? = null,
    val error: String? = null
)
