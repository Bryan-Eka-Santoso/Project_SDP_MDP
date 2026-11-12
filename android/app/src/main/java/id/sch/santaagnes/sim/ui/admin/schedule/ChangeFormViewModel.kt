package id.sch.santaagnes.sim.ui.admin.schedule

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepository
import id.sch.santaagnes.sim.domain.model.AdminSlot
import id.sch.santaagnes.sim.domain.model.ChangeInput
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.RoomOption
import id.sch.santaagnes.sim.domain.model.ScheduleLookups
import id.sch.santaagnes.sim.domain.model.TeacherOption
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ChangeFormUiState(
    val slots: List<AdminSlot> = emptyList(),
    val lookups: ScheduleLookups? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    // galat dari server atau jaringan; galat per field ada di *Error di bawah
    val errorMessage: String? = null,
    @param:StringRes val slotError: Int? = null,
    @param:StringRes val dateError: Int? = null,
    @param:StringRes val typeError: Int? = null,
    @param:StringRes val descriptionError: Int? = null,
    @param:StringRes val teacherError: Int? = null,
    @param:StringRes val rescheduleError: Int? = null
) {
    val isReady: Boolean get() = lookups != null
}

@HiltViewModel
class ChangeFormViewModel @Inject constructor(
    private val repository: AdminScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeFormUiState())
    val uiState: StateFlow<ChangeFormUiState> = _uiState.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Dipancarkan sekali setelah perubahan tersimpan di server. */
    val saved = savedChannel.receiveAsFlow()

    fun load() {
        if (_uiState.value.isReady) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val slots = repository.allSlots()
            val lookups = repository.lookups()
            when {
                slots is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = slots.message) }
                lookups is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = lookups.message) }
                slots is Resource.Success && lookups is Resource.Success -> _uiState.update {
                    it.copy(
                        slots = slots.data.sortedWith(
                            compareBy({ s -> s.classGroupName }, { s -> s.subjectName }, { s -> s.dayOfWeek }, { s -> s.startTime })
                        ),
                        lookups = lookups.data,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun save(
        slot: AdminSlot?,
        date: LocalDate?,
        type: ChangeType?,
        description: String,
        teacher: TeacherOption?,
        room: RoomOption?,
        start: LocalTime?,
        end: LocalTime?
    ) {
        if (_uiState.value.isSaving) return

        val text = description.trim()
        // Jenis perubahan menentukan kolom yang wajib; kolom lain dikosongkan agar tidak ikut terkirim.
        val rescheduleError = if (type == ChangeType.RESCHEDULE) {
            when {
                room == null && start == null && end == null -> R.string.change_error_reschedule
                (start == null) != (end == null) -> R.string.change_error_time_pair
                start != null && end != null && !end.isAfter(start) -> R.string.slot_error_order
                else -> null
            }
        } else {
            null
        }
        val state = _uiState.value.copy(
            errorMessage = null,
            slotError = if (slot == null) R.string.change_error_slot else null,
            dateError = if (date == null) R.string.change_error_date else null,
            typeError = if (type == null) R.string.change_error_type else null,
            descriptionError = when {
                text.isEmpty() -> R.string.change_error_description
                text.length > DESCRIPTION_MAX -> R.string.change_error_description_long
                else -> null
            },
            teacherError = if (type == ChangeType.SUBSTITUTE && teacher == null) R.string.change_error_teacher else null,
            rescheduleError = rescheduleError
        )
        val valid = state.slotError == null && state.dateError == null && state.typeError == null &&
            state.descriptionError == null && state.teacherError == null && state.rescheduleError == null
        if (!valid) {
            _uiState.value = state
            return
        }

        _uiState.value = state.copy(isSaving = true)
        val input = ChangeInput(
            slotId = checkNotNull(slot).id,
            date = checkNotNull(date),
            type = checkNotNull(type),
            description = text,
            substituteTeacherId = teacher?.id.takeIf { type == ChangeType.SUBSTITUTE },
            newRoomId = room?.id.takeIf { type == ChangeType.RESCHEDULE },
            newStartTime = start.takeIf { type == ChangeType.RESCHEDULE },
            newEndTime = end.takeIf { type == ChangeType.RESCHEDULE }
        )
        viewModelScope.launch {
            when (val result = repository.saveChange(input)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    savedChannel.send(Unit)
                }
                // Bentrok jadwal atau galat validasi server tampil apa adanya.
                is Resource.Error -> _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
            }
        }
    }

    private companion object {
        const val DESCRIPTION_MAX = 255
    }
}
