package id.sch.santaagnes.sim.ui.schedule

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.RescheduleRepository
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.RescheduleInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class RescheduleFormUiState(
    val rooms: List<NamedId> = emptyList(),
    val isLoadingRooms: Boolean = true,
    val isSaving: Boolean = false,
    // galat dari server (bentrok, jaringan, dll.); galat per field ada di *Error di bawah
    val errorMessage: String? = null,
    @param:StringRes val dateError: Int? = null,
    @param:StringRes val timeError: Int? = null,
    @param:StringRes val descriptionError: Int? = null
)

/** Pertemuan yang dipindah: jadwal aslinya menurut slot mingguan. */
data class RescheduleTarget(
    val slotId: Int,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/** Guru memindah atau membatalkan satu pertemuan miliknya (F-JDW-04). */
@HiltViewModel
class RescheduleFormViewModel @Inject constructor(
    private val repository: RescheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RescheduleFormUiState())
    val uiState: StateFlow<RescheduleFormUiState> = _uiState.asStateFlow()

    private val doneChannel = Channel<Int>(Channel.BUFFERED)

    /** Dipancarkan sekali setelah berhasil; isinya string resource pesan untuk pengguna. */
    val done = doneChannel.receiveAsFlow()

    fun loadRooms() {
        // Sudah termuat (mis. layar diputar): tidak perlu mengambil ulang.
        if (_uiState.value.rooms.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRooms = true, errorMessage = null) }
            when (val result = repository.rooms()) {
                is Resource.Success -> _uiState.update { it.copy(rooms = result.data, isLoadingRooms = false) }
                is Resource.Error -> _uiState.update {
                    it.copy(isLoadingRooms = false, errorMessage = result.message)
                }
            }
        }
    }

    /** Field yang null berarti tidak diubah; minimal satu dari tanggal, jam, atau ruangan harus berubah. */
    fun save(
        target: RescheduleTarget,
        newDate: LocalDate?,
        newStartTime: LocalTime?,
        newEndTime: LocalTime?,
        room: NamedId?,
        description: String
    ) {
        if (_uiState.value.isSaving) return

        val movedDate = newDate?.takeIf { it != target.date }
        val timeChanged = newStartTime != null && newEndTime != null &&
            (newStartTime != target.startTime || newEndTime != target.endTime)
        val timeError = timeError(newStartTime, newEndTime)
        val dateError = dateError(target.date, movedDate)
            ?: if (timeError == null && movedDate == null && !timeChanged && room == null) {
                R.string.reschedule_error_nothing
            } else {
                null
            }
        val state = _uiState.value.copy(
            errorMessage = null,
            dateError = dateError,
            timeError = timeError,
            descriptionError = descriptionError(description)
        )
        if (state.dateError != null || state.timeError != null || state.descriptionError != null) {
            _uiState.value = state
            return
        }

        _uiState.value = state.copy(isSaving = true)
        val input = RescheduleInput(
            slotId = target.slotId,
            date = target.date,
            newDate = movedDate,
            newStartTime = newStartTime,
            newEndTime = newEndTime,
            newRoomId = room?.id,
            description = description
        )
        viewModelScope.launch { finish(repository.reschedule(input), R.string.reschedule_saved) }
    }

    /** Keterangan wajib diisi sebelum kelas dibatalkan; false (dan galat tampil) bila belum layak. */
    fun checkCancelDescription(description: String): Boolean {
        val error = descriptionError(description)
        _uiState.update { it.copy(descriptionError = error, errorMessage = null) }
        return error == null
    }

    /** Batalkan kelas pada satu pertemuan; siswa dan wali murid diberi tahu. */
    fun cancelClass(target: RescheduleTarget, description: String) {
        if (_uiState.value.isSaving || !checkCancelDescription(description)) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            finish(repository.cancelClass(target.slotId, target.date, description), R.string.reschedule_class_cancelled)
        }
    }

    /** Hapus pemindahan atau pembatalan yang sudah tersimpan; pertemuan kembali ke jadwal aslinya. */
    fun cancelMove(changeId: Int, @StringRes message: Int = R.string.reschedule_cancelled) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch { finish(repository.cancel(changeId), message) }
    }

    private fun descriptionError(description: String): Int? = when {
        description.isBlank() -> R.string.change_error_description
        description.trim().length > MAX_DESCRIPTION -> R.string.change_error_description_long
        else -> null
    }

    private suspend fun finish(result: Resource<Unit>, @StringRes message: Int) {
        when (result) {
            is Resource.Success -> {
                _uiState.update { it.copy(isSaving = false) }
                doneChannel.send(message)
            }
            is Resource.Error -> _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
        }
    }

    // Aturan yang sama dengan server (BUSINESS_RULES 2.4), supaya pesannya muncul sebelum mengirim.
    private fun dateError(original: LocalDate, movedDate: LocalDate?): Int? = when {
        movedDate == null -> null
        movedDate.isBefore(DateTimeWib.today()) -> R.string.reschedule_error_date_past
        movedDate.dayOfWeek == DayOfWeek.SUNDAY -> R.string.reschedule_error_sunday
        movedDate.dayOfWeek == original.dayOfWeek -> R.string.reschedule_error_same_weekday
        else -> null
    }

    private fun timeError(start: LocalTime?, end: LocalTime?): Int? = when {
        (start == null) != (end == null) -> R.string.change_error_time_pair
        start != null && end != null && !end.isAfter(start) -> R.string.reschedule_error_time_order
        else -> null
    }

    private companion object {
        const val MAX_DESCRIPTION = 255
    }
}
