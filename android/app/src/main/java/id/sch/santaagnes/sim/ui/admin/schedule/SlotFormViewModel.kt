package id.sch.santaagnes.sim.ui.admin.schedule

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepository
import id.sch.santaagnes.sim.domain.model.AssignmentOption
import id.sch.santaagnes.sim.domain.model.RoomOption
import id.sch.santaagnes.sim.domain.model.ScheduleLookups
import id.sch.santaagnes.sim.domain.model.SlotInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

data class SlotFormUiState(
    val lookups: ScheduleLookups? = null,
    val isLoadingLookups: Boolean = true,
    val isSaving: Boolean = false,
    // galat dari server (jaringan, bentrok jadwal, validasi); galat per field ada di *Error di bawah
    val errorMessage: String? = null,
    @param:StringRes val assignmentError: Int? = null,
    @param:StringRes val roomError: Int? = null,
    @param:StringRes val dayError: Int? = null,
    @param:StringRes val startError: Int? = null,
    @param:StringRes val endError: Int? = null
)

@HiltViewModel
class SlotFormViewModel @Inject constructor(
    private val repository: AdminScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlotFormUiState())
    val uiState: StateFlow<SlotFormUiState> = _uiState.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Dipancarkan sekali setelah slot tersimpan di server. */
    val saved = savedChannel.receiveAsFlow()

    fun loadLookups() {
        if (_uiState.value.lookups != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLookups = true, errorMessage = null) }
            when (val result = repository.lookups()) {
                is Resource.Success -> _uiState.update { it.copy(lookups = result.data, isLoadingLookups = false) }
                is Resource.Error -> _uiState.update { it.copy(isLoadingLookups = false, errorMessage = result.message) }
            }
        }
    }

    /** `slotId` null = slot baru; selain itu ubah slot tersebut. */
    fun save(
        slotId: Int?,
        assignment: AssignmentOption?,
        room: RoomOption?,
        day: DayOfWeek?,
        start: LocalTime?,
        end: LocalTime?
    ) {
        if (_uiState.value.isSaving) return

        val state = _uiState.value.copy(
            errorMessage = null,
            assignmentError = if (assignment == null) R.string.slot_error_assignment else null,
            roomError = if (room == null) R.string.slot_error_room else null,
            dayError = if (day == null) R.string.slot_error_day else null,
            startError = if (start == null) R.string.slot_error_start else null,
            endError = when {
                end == null -> R.string.slot_error_end
                start != null && !end.isAfter(start) -> R.string.slot_error_order
                else -> null
            }
        )
        val valid = state.assignmentError == null && state.roomError == null && state.dayError == null &&
            state.startError == null && state.endError == null
        if (!valid) {
            _uiState.value = state
            return
        }

        _uiState.value = state.copy(isSaving = true)
        val input = SlotInput(
            assignmentId = checkNotNull(assignment).id,
            roomId = checkNotNull(room).id,
            dayOfWeek = checkNotNull(day),
            startTime = checkNotNull(start),
            endTime = checkNotNull(end)
        )
        viewModelScope.launch {
            val result = if (slotId == null) repository.createSlot(input) else repository.updateSlot(slotId, input)
            when (result) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    savedChannel.send(Unit)
                }
                // Bentrok jadwal (409) tampil di sini lengkap dengan slot yang bertabrakan.
                is Resource.Error -> _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
            }
        }
    }
}
