package id.sch.santaagnes.sim.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.repository.CalendarSyncRepository
import id.sch.santaagnes.sim.domain.model.CalendarSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarSyncUiState(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val lastSyncMillis: Long? = null,
    val result: CalendarSyncResult? = null,
    val errorMessage: String? = null,
    // Siswa dan guru juga mendapat peminjaman yang disetujui; role lain hanya agenda akademik.
    val includesBookings: Boolean = false
)

/** Tombol "Sinkronkan ke kalender perangkat" di tab Profil (F-JDW-06). */
@HiltViewModel
class CalendarSyncViewModel @Inject constructor(
    private val repository: CalendarSyncRepository,
    private val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarSyncUiState())
    val uiState: StateFlow<CalendarSyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = session.getUserId() ?: return@launch
            val role = session.getRole()
            _uiState.update {
                it.copy(
                    enabled = repository.isEnabled(userId),
                    lastSyncMillis = repository.lastSyncMillis(userId),
                    includesBookings = role == "STUDENT" || role == "TEACHER"
                )
            }
        }
    }

    /** Dipanggil setelah izin kalender diberikan (atau sudah ada) saat pengguna menyalakan tombol. */
    fun enable() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            val userId = session.getUserId() ?: return@launch
            val role = session.getRole() ?: return@launch
            // enabled langsung true agar tombol tidak melompat kembali selama sinkron berjalan.
            _uiState.update { it.copy(busy = true, enabled = true, errorMessage = null) }
            when (val result = repository.enable(userId, role)) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        busy = false,
                        enabled = true,
                        result = result.data,
                        lastSyncMillis = System.currentTimeMillis()
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(busy = false, enabled = false, errorMessage = result.message)
                }
            }
        }
    }

    fun disable() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            val userId = session.getUserId() ?: return@launch
            _uiState.update { it.copy(busy = true, enabled = false, errorMessage = null) }
            repository.disable(userId)
            _uiState.update {
                it.copy(busy = false, enabled = false, result = null, lastSyncMillis = null)
            }
        }
    }

    fun permissionDenied() {
        _uiState.update {
            it.copy(enabled = false, errorMessage = "Izin kalender ditolak, sinkronisasi tidak dinyalakan.")
        }
    }
}
