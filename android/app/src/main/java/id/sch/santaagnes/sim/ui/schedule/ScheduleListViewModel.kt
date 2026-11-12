package id.sch.santaagnes.sim.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.ScheduleRepository
import id.sch.santaagnes.sim.domain.model.ScheduleScope
import id.sch.santaagnes.sim.domain.model.ScheduleViewer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleListViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    private data class RefreshState(
        val isLoading: Boolean = true,
        val errorMessage: String? = null
    )

    private val initialWeek = DateTimeWib.currentWeekStart(DateTimeWib.today())

    private val viewer = MutableStateFlow<ScheduleViewer?>(null)
    private val selectedOption = MutableStateFlow(0)
    private val weekStart = MutableStateFlow(initialWeek)
    private val selectedDate = MutableStateFlow(defaultDay(initialWeek))
    private val refreshState = MutableStateFlow(RefreshState())

    // null sampai data sesi terbaca, atau selamanya bila akun tidak punya pilihan jadwal.
    private val scopeOrNull = combine(viewer, selectedOption) { v, index ->
        v?.options?.getOrNull(index)?.scope
    }
    private val scope = scopeOrNull.filterNotNull()

    // Cache (Room) tampil lebih dulu; hasil dari server masuk lewat cache yang sama.
    private val weekEntries = combine(scopeOrNull, weekStart) { s, start -> s to start }
        .flatMapLatest { (s, start) ->
            if (s == null) flowOf(emptyList()) else repository.observe(s, start, DateTimeWib.weekEnd(start))
        }

    private val viewerState = combine(viewer, selectedOption) { v, index -> v to index }

    val uiState: StateFlow<ScheduleUiState> = combine(
        weekStart, selectedDate, weekEntries, refreshState, viewerState
    ) { start, date, entries, refresh, (v, index) ->
        val chosen = v?.options?.getOrNull(index)?.scope
        ScheduleUiState(
            weekStart = start,
            weekEnd = DateTimeWib.weekEnd(start),
            selectedDate = date,
            lessons = entries.filter { it.date == date },
            isLoading = refresh.isLoading,
            errorMessage = refresh.errorMessage,
            role = v?.role,
            options = v?.options.orEmpty(),
            selectedOption = index,
            showTeacher = !(v?.role == "TEACHER" && chosen == ScheduleScope.Own)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(
            weekStart = initialWeek,
            weekEnd = DateTimeWib.weekEnd(initialWeek),
            selectedDate = selectedDate.value,
            isLoading = true
        )
    )

    init {
        viewModelScope.launch {
            val loaded = repository.viewer()
            selectedOption.value = loaded.initialIndex
            viewer.value = loaded
            if (loaded.options.isEmpty()) refreshState.value = RefreshState(isLoading = false)
        }
        // Setiap pekan atau lingkup yang dibuka dimuat ulang dari server; berpindah membatalkan muat sebelumnya.
        viewModelScope.launch {
            combine(scope, weekStart) { s, start -> s to start }
                .collectLatest { (s, start) -> refresh(s, start) }
        }
    }

    fun previousWeek() = showWeek(weekStart.value.minusWeeks(1))

    fun nextWeek() = showWeek(weekStart.value.plusWeeks(1))

    fun thisWeek() = showWeek(DateTimeWib.currentWeekStart(DateTimeWib.today()))

    fun showWeek(start: LocalDate) {
        selectedDate.value = defaultDay(start)
        weekStart.value = start
    }

    /** Hari yang dibuka pada tab hari; hanya tanggal di dalam pekan yang ditampilkan. */
    fun selectDate(date: LocalDate) {
        val start = weekStart.value
        if (date in start..DateTimeWib.weekEnd(start)) selectedDate.value = date
    }

    fun selectOption(index: Int) {
        val options = viewer.value?.options ?: return
        if (index !in options.indices || index == selectedOption.value) return
        selectedOption.value = index
        viewModelScope.launch { repository.remember(options[index].scope) }
    }

    fun retry() {
        val s = viewer.value?.options?.getOrNull(selectedOption.value)?.scope ?: return
        viewModelScope.launch { refresh(s, weekStart.value) }
    }

    private suspend fun refresh(scope: ScheduleScope, start: LocalDate) {
        refreshState.value = RefreshState(isLoading = true)
        refreshState.value = when (val result = repository.refresh(scope, start, DateTimeWib.weekEnd(start))) {
            is Resource.Success -> RefreshState(isLoading = false)
            is Resource.Error -> RefreshState(isLoading = false, errorMessage = result.message)
        }
    }

    // Hari ini bila termasuk pekan itu (Senin-Sabtu), selain itu Senin.
    private fun defaultDay(start: LocalDate): LocalDate {
        val today = DateTimeWib.today()
        return if (today in start..DateTimeWib.weekEnd(start)) today else start
    }
}
