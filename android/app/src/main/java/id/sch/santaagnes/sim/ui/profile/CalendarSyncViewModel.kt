package id.sch.santaagnes.sim.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.repository.CalendarSyncRepository
import id.sch.santaagnes.sim.domain.model.CalendarSyncResult
import id.sch.santaagnes.sim.domain.model.DeviceCalendarAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarSyncUiState(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val hasPermission: Boolean = false,
    val calendars: List<DeviceCalendarAccount> = emptyList(),
    val selectedCalendarId: Long? = null,
    val calendarLabel: String? = null,
    val lastSyncMillis: Long? = null,
    val result: CalendarSyncResult? = null,
    val errorMessage: String? = null,
    // Siswa dan guru juga mendapat peminjaman yang disetujui; role lain hanya agenda akademik.
    val includesBookings: Boolean = false
)

/** Kontrol sinkronisasi Google Calendar / kalender perangkat (F-JDW-06), dipakai di Kalender Akademik & Profil. */
@HiltViewModel
class CalendarSyncViewModel @Inject constructor(
    private val repository: CalendarSyncRepository,
    private val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarSyncUiState())
    val uiState: StateFlow<CalendarSyncUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    /** Muat ulang status sinkron dan daftar akun Google Calendar di perangkat. */
    fun refreshState() {
        viewModelScope.launch { refreshStateInternal() }
    }

    /** Muat ulang daftar akun Google Calendar terbaru dari perangkat lalu kembalikan ke pemanggil. */
    suspend fun loadAvailableCalendars(): List<DeviceCalendarAccount> = refreshStateInternal()

    private suspend fun refreshStateInternal(): List<DeviceCalendarAccount> {
        val userId = session.getUserId() ?: return emptyList()
        val role = session.getRole()
        val hasPermission = repository.hasCalendarPermission()
        val calendars = if (hasPermission) repository.availableCalendars() else emptyList()
        val selectedId = repository.selectedCalendarId(userId) ?: calendars.firstOrNull()?.id
        val label = repository.activeCalendarLabel(userId)
            ?: calendars.firstOrNull { it.id == selectedId }?.shortLabel
            ?: calendars.firstOrNull()?.shortLabel
        _uiState.update {
            it.copy(
                enabled = repository.isEnabled(userId),
                hasPermission = hasPermission,
                calendars = calendars,
                selectedCalendarId = selectedId,
                calendarLabel = label,
                lastSyncMillis = repository.lastSyncMillis(userId),
                // Termasuk hasil sinkron otomatis, agar jumlah yang diperbarui ikut tampil.
                result = repository.lastResult(userId),
                includesBookings = role == "STUDENT" || role == "TEACHER"
            )
        }
        return calendars
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
                is Resource.Success -> applySyncSuccess(userId, result.data)
                is Resource.Error -> _uiState.update {
                    it.copy(
                        busy = false,
                        enabled = false,
                        hasPermission = repository.hasCalendarPermission(),
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /** Paksa sinkronisasi segera ke Google Calendar (melewati interval 10 menit). */
    fun syncNow() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            val userId = session.getUserId() ?: return@launch
            val role = session.getRole() ?: return@launch
            _uiState.update { it.copy(busy = true, errorMessage = null) }
            when (val result = repository.syncNow(userId, role)) {
                is Resource.Success -> applySyncSuccess(userId, result.data)
                is Resource.Error -> _uiState.update {
                    it.copy(
                        busy = false,
                        hasPermission = repository.hasCalendarPermission(),
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /** Pilih akun Google Calendar tujuan dan langsung sinkronkan agenda ke akun tersebut. */
    fun selectCalendar(calendarId: Long) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            val userId = session.getUserId() ?: return@launch
            val role = session.getRole() ?: return@launch
            _uiState.update { it.copy(busy = true, enabled = true, errorMessage = null) }
            when (val result = repository.selectCalendar(userId, role, calendarId)) {
                is Resource.Success -> applySyncSuccess(userId, result.data)
                is Resource.Error -> _uiState.update {
                    it.copy(
                        busy = false,
                        enabled = repository.isEnabled(userId),
                        hasPermission = repository.hasCalendarPermission(),
                        errorMessage = result.message
                    )
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
            it.copy(
                enabled = false,
                hasPermission = false,
                errorMessage = "Izin kalender ditolak. Berikan izin kalender untuk menyinkronkan ke Google Calendar."
            )
        }
    }

    private suspend fun applySyncSuccess(userId: Int, data: CalendarSyncResult) {
        val calendars = repository.availableCalendars()
        val selectedId = repository.selectedCalendarId(userId) ?: calendars.firstOrNull()?.id
        val label = data.accountLabel
            ?: repository.activeCalendarLabel(userId)
            ?: calendars.firstOrNull { it.id == selectedId }?.shortLabel
        _uiState.update {
            it.copy(
                busy = false,
                enabled = true,
                hasPermission = true,
                calendars = calendars,
                selectedCalendarId = selectedId,
                calendarLabel = label,
                result = data,
                lastSyncMillis = System.currentTimeMillis(),
                errorMessage = null
            )
        }
    }
}
