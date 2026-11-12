package id.sch.santaagnes.sim.ui.admin.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepository
import id.sch.santaagnes.sim.domain.model.AdminSlot
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.SlotFilter
import id.sch.santaagnes.sim.domain.model.TeacherOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class AdminScheduleUiState(
    val filter: SlotFilter = SlotFilter(),
    val slots: List<AdminSlot> = emptyList(),
    // muat halaman pertama (daftar lama tetap tampil selama itu)
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    // pilihan filter; kosong bila master penugasan belum tersedia, filter hari tetap jalan
    val classGroups: List<NamedId> = emptyList(),
    val teachers: List<TeacherOption> = emptyList()
) {
    val isEmpty: Boolean get() = slots.isEmpty() && !isLoading && errorMessage == null
}

@HiltViewModel
class AdminScheduleViewModel @Inject constructor(
    private val repository: AdminScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminScheduleUiState())
    val uiState: StateFlow<AdminScheduleUiState> = _uiState.asStateFlow()

    private var nextPage = 1

    init {
        loadFilterOptions()
    }

    /** Muat ulang dari halaman pertama; dipanggil saat layar tampil (termasuk setelah kembali dari formulir). */
    fun refresh() {
        if (_uiState.value.isLoadingMore) return
        val filter = _uiState.value.filter
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.slots(1, filter)) {
                is Resource.Success -> {
                    // Filter diganti selagi memuat: hasil lama dibuang.
                    if (_uiState.value.filter != filter) return@launch
                    nextPage = 2
                    _uiState.update {
                        it.copy(slots = result.data.items, hasMore = result.data.hasMore, isLoading = false)
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading || state.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            when (val result = repository.slots(nextPage, state.filter)) {
                is Resource.Success -> {
                    if (_uiState.value.filter != state.filter) return@launch
                    nextPage += 1
                    _uiState.update { current ->
                        // Penyaring id mencegah ganda bila daftar bergeser di server di antara dua halaman.
                        val known = current.slots.map { it.id }.toSet()
                        current.copy(
                            slots = current.slots + result.data.items.filter { it.id !in known },
                            hasMore = result.data.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoadingMore = false, errorMessage = result.message) }
            }
        }
    }

    fun selectDay(day: DayOfWeek?) = changeFilter { it.copy(dayOfWeek = day) }

    fun selectClassGroup(id: Int?) = changeFilter { it.copy(classGroupId = id) }

    fun selectTeacher(id: Int?) = changeFilter { it.copy(teacherId = id) }

    fun delete(slotId: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteSlot(slotId)) {
                is Resource.Success -> _uiState.update { state ->
                    state.copy(slots = state.slots.filterNot { it.id == slotId }, errorMessage = null)
                }
                is Resource.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    private fun changeFilter(change: (SlotFilter) -> SlotFilter) {
        val updated = change(_uiState.value.filter)
        if (updated == _uiState.value.filter) return
        _uiState.update { it.copy(filter = updated) }
        refresh()
    }

    // Kegagalan dibiarkan senyap: tanpa pilihan, layar tetap berfungsi dengan filter hari.
    private fun loadFilterOptions() {
        viewModelScope.launch {
            val result = repository.lookups()
            if (result is Resource.Success) {
                _uiState.update {
                    it.copy(classGroups = result.data.classGroups, teachers = result.data.teachers)
                }
            }
        }
    }
}
