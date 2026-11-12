package id.sch.santaagnes.sim.ui.schedule

import id.sch.santaagnes.sim.domain.model.ScheduleEntry
import id.sch.santaagnes.sim.domain.model.ScopeOption
import java.time.LocalDate

data class ScheduleUiState(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val selectedDate: LocalDate,
    // pelajaran pada selectedDate saja (data pekan penuh tetap di cache)
    val lessons: List<ScheduleEntry> = emptyList(),
    // sedang mengambil dari server; isi cache tetap tampil selama itu
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val role: String? = null,
    val options: List<ScopeOption> = emptyList(),
    val selectedOption: Int = 0,
    // guru melihat jadwal mengajarnya sendiri: tampilkan kelas, bukan nama guru
    val showTeacher: Boolean = true
) {
    val isEmpty: Boolean get() = lessons.isEmpty() && !isLoading && errorMessage == null
}
