package id.sch.santaagnes.sim.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.CalendarRepository
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Sel tunggal pada grid kalender bulanan; `date == null` berarti slot pengisi sebelum/sesudah bulan. */
data class CalendarDayCell(
    val date: LocalDate?,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val isSunday: Boolean = false,
    val eventTypes: Set<AcademicEventType> = emptySet()
)

data class AcademicCalendarUiState(
    val role: String? = null,
    val events: List<AcademicEvent> = emptyList(),
    val selectedType: AcademicEventType? = null,
    val searchQuery: String = "",
    val displayedMonth: YearMonth = YearMonth.from(DateTimeWib.today()),
    val selectedDate: LocalDate? = null,
    val showAllMonths: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val isAdmin: Boolean get() = role == "ADMIN"

    /** Daftar agenda yang ditampilkan di bawah kalender sesuai pilihan tanggal/bulan/pencarian. */
    val agendaEvents: List<AcademicEvent>
        get() {
            val activeSelected = selectedDate
            if (activeSelected != null) {
                return events.filter { overlapsRange(it, activeSelected, activeSelected) }
            }
            if (showAllMonths || searchQuery.isNotBlank()) {
                return events
            }
            val monthStart = displayedMonth.atDay(1)
            val monthEnd = displayedMonth.atEndOfMonth()
            return events.filter { overlapsRange(it, monthStart, monthEnd) }
        }

    /** Membentuk sel grid 7 kolom (Senin–Minggu) untuk bulan yang sedang ditampilkan. */
    fun calendarDays(today: LocalDate = DateTimeWib.today()): List<CalendarDayCell> {
        val firstDay = displayedMonth.atDay(1)
        val leadingEmpty = firstDay.dayOfWeek.value - 1
        val daysInMonth = displayedMonth.lengthOfMonth()
        val parsedEvents = events.mapNotNull { ev ->
            val start = runCatching { LocalDate.parse(ev.startDate) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalDate.parse(ev.endDate) }.getOrNull() ?: start
            Triple(start, end, ev.type)
        }

        val cells = ArrayList<CalendarDayCell>(42)
        repeat(leadingEmpty) { cells.add(CalendarDayCell(date = null)) }
        for (day in 1..daysInMonth) {
            val date = displayedMonth.atDay(day)
            val types = parsedEvents
                .filter { (start, end, _) -> !date.isBefore(start) && !date.isAfter(end) }
                .mapTo(mutableSetOf()) { it.third }
            cells.add(
                CalendarDayCell(
                    date = date,
                    isToday = date == today,
                    isSelected = date == selectedDate,
                    isSunday = date.dayOfWeek == DayOfWeek.SUNDAY,
                    eventTypes = types
                )
            )
        }
        val trailing = (7 - (cells.size % 7)) % 7
        repeat(trailing) { cells.add(CalendarDayCell(date = null)) }
        return cells
    }

    private fun overlapsRange(event: AcademicEvent, rangeStart: LocalDate, rangeEnd: LocalDate): Boolean {
        val start = runCatching { LocalDate.parse(event.startDate) }.getOrNull() ?: return false
        val end = runCatching { LocalDate.parse(event.endDate) }.getOrNull() ?: start
        return !end.isBefore(rangeStart) && !start.isAfter(rangeEnd)
    }
}

/** Mengelola daftar & tampilan kalender bulanan agenda akademik (F-KAL-02 semua role, F-KAL-01 Admin). */
@HiltViewModel
class AcademicCalendarViewModel @Inject constructor(
    private val repository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AcademicCalendarUiState())
    val uiState: StateFlow<AcademicCalendarUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    private val _eventsMutated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventsMutated: SharedFlow<Unit> = _eventsMutated.asSharedFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(role = repository.currentRole()) }
            load()
        }
    }

    fun setTypeFilter(type: AcademicEventType?) {
        _uiState.update { it.copy(selectedType = type) }
        load(type = type, search = _uiState.value.searchQuery)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        load(type = _uiState.value.selectedType, search = query)
    }

    fun previousMonth() {
        _uiState.update {
            it.copy(
                displayedMonth = it.displayedMonth.minusMonths(1),
                selectedDate = null,
                showAllMonths = false
            )
        }
    }

    fun nextMonth() {
        _uiState.update {
            it.copy(
                displayedMonth = it.displayedMonth.plusMonths(1),
                selectedDate = null,
                showAllMonths = false
            )
        }
    }

    fun goToToday() {
        val today = DateTimeWib.today()
        _uiState.update {
            it.copy(
                displayedMonth = YearMonth.from(today),
                selectedDate = today,
                showAllMonths = false
            )
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { state ->
            val toggled = if (state.selectedDate == date) null else date
            state.copy(
                displayedMonth = YearMonth.from(date),
                selectedDate = toggled,
                showAllMonths = false
            )
        }
    }

    fun clearDateSelection() {
        _uiState.update { it.copy(selectedDate = null) }
    }

    fun toggleShowAllMonths() {
        _uiState.update {
            it.copy(selectedDate = null, showAllMonths = !it.showAllMonths)
        }
    }

    fun load(
        type: AcademicEventType? = _uiState.value.selectedType,
        search: String = _uiState.value.searchQuery
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getEvents(type = type?.wire, search = search)) {
                is Resource.Success -> _uiState.update {
                    it.copy(events = res.data, isLoading = false)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    _message.emit(res.message)
                }
            }
        }
    }

    fun saveEvent(
        editingId: Int?,
        title: String,
        type: AcademicEventType,
        startDate: String,
        endDate: String,
        description: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val res = if (editingId == null) {
                repository.createEvent(title, type, startDate, endDate, description)
            } else {
                repository.updateEvent(editingId, title, type, startDate, endDate, description)
            }

            when (res) {
                is Resource.Success -> {
                    val savedStart = runCatching { LocalDate.parse(startDate) }.getOrNull()
                    _uiState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            displayedMonth = savedStart?.let { YearMonth.from(it) } ?: state.displayedMonth,
                            selectedDate = savedStart ?: state.selectedDate,
                            showAllMonths = false
                        )
                    }
                    _message.emit(
                        if (editingId == null) {
                            "Agenda kalender berhasil ditambahkan"
                        } else {
                            "Agenda kalender berhasil diperbarui"
                        }
                    )
                    _eventsMutated.emit(Unit)
                    load()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(res.message)
                }
            }
        }
    }

    fun deleteEvent(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val res = repository.deleteEvent(id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit("Agenda kalender berhasil dihapus")
                    _eventsMutated.emit(Unit)
                    load()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(res.message)
                }
            }
        }
    }
}
