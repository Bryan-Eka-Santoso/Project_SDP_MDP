package id.sch.santaagnes.sim.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.repository.TaskRepository
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    // null sampai data sesi terbaca
    val role: String? = null,
    val name: String? = null,
    // wali murid: anak-anaknya dan yang sedang dilihat
    val children: List<NamedId> = emptyList(),
    val selectedChildId: Int? = null,
    val reminders: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isGuardian: Boolean get() = role == "GUARDIAN"

    /** Wali murid tanpa anak terhubung tidak punya tugas yang bisa ditampilkan. */
    val hasNoChild: Boolean get() = isGuardian && children.isEmpty()

    val isRemindersEmpty: Boolean
        get() = reminders.isEmpty() && !isLoading && errorMessage == null && !hasNoChild
}

/** Beranda siswa dan wali murid: sapaan, pengingat tugas terdekat, dan pintasan. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: SessionManager,
    private val tasks: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val role = session.getRole()
            val children = if (role == "GUARDIAN") session.getChildren() else emptyList()
            val active = session.getActiveStudentId()
            val selected = children.firstOrNull { it.id == active }?.id ?: children.firstOrNull()?.id
            _uiState.update {
                it.copy(role = role, name = session.getUserName(), children = children, selectedChildId = selected)
            }
            refresh()
        }
    }

    /** Muat ulang pengingat; dipanggil saat layar tampil agar tanda selesai dan tugas baru ikut terbaca. */
    fun refresh() {
        val state = _uiState.value
        if (state.role == null) return
        if (state.hasNoChild) {
            _uiState.update { it.copy(isLoading = false, reminders = emptyList()) }
            return
        }
        val child = state.selectedChildId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = tasks.reminders(studentId = child, limit = REMINDER_COUNT)) {
                is Resource.Success -> {
                    // Anak diganti selagi memuat: hasil lama dibuang.
                    if (_uiState.value.selectedChildId != child) return@launch
                    _uiState.update { it.copy(reminders = result.data, isLoading = false) }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun selectChild(childId: Int) {
        if (childId == _uiState.value.selectedChildId) return
        _uiState.update { it.copy(selectedChildId = childId, reminders = emptyList()) }
        viewModelScope.launch { session.saveActiveStudentId(childId) }
        refresh()
    }

    private companion object {
        const val REMINDER_COUNT = 3
    }
}
