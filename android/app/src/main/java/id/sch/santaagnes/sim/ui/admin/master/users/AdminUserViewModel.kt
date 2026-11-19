package id.sch.santaagnes.sim.ui.admin.master.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.UserRepository
import id.sch.santaagnes.sim.domain.model.MasterUser
import id.sch.santaagnes.sim.domain.model.UserFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUserUiState(
    val users: List<MasterUser> = emptyList(),
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val inactiveCount: Int = 0,
    val activePeriodText: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentFilter: UserFilter = UserFilter()
)

@HiltViewModel
class AdminUserViewModel @Inject constructor(
    private val repository: UserRepository,
    private val academicYearRepository: AcademicYearRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUserUiState())
    val uiState: StateFlow<AdminUserUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        loadPeriod()
        loadUsers()
    }

    private fun loadPeriod() {
        viewModelScope.launch {
            val res = academicYearRepository.getActiveSemester()
            if (res is Resource.Success) {
                val sem = res.data
                val year = sem.academicYearName ?: "-"
                _uiState.value = _uiState.value.copy(activePeriodText = "${sem.label} · $year")
            }
        }
    }

    fun loadUsers(filter: UserFilter = _uiState.value.currentFilter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                currentFilter = filter
            )

            when (val result = repository.getUsers(page = 1, limit = 50, filter = filter)) {
                is Resource.Success -> {
                    val users = result.data.first
                    val total = result.data.second
                    val active = users.count { it.isActive }
                    val inactive = users.size - active

                    _uiState.value = _uiState.value.copy(
                        users = users,
                        totalCount = total,
                        activeCount = active,
                        inactiveCount = inactive,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun setRoleFilter(role: String?) {
        loadUsers(_uiState.value.currentFilter.copy(role = role))
    }

    fun setStatusFilter(status: String?) {
        val current = _uiState.value.currentFilter.status
        val next = if (current == status) null else status
        loadUsers(_uiState.value.currentFilter.copy(status = next))
    }

    fun setSearchQuery(query: String) {
        loadUsers(_uiState.value.currentFilter.copy(search = query.trim()))
    }

    fun createUser(name: String, email: String, password: String, role: String) {
        viewModelScope.launch {
            when (val result = repository.createUser(email, password, name, role)) {
                is Resource.Success -> {
                    _userMessage.emit("Pengguna ${result.data.name} berhasil ditambahkan")
                    loadUsers()
                }
                is Resource.Error -> _userMessage.emit(result.message)
            }
        }
    }

    /** Ubah data dasar dan/atau role pengguna (F-MST-01, F-MST-02). */
    fun updateUser(
        user: MasterUser,
        newName: String,
        newEmail: String,
        newRole: String
    ) {
        viewModelScope.launch {
            val infoChanged = newName != user.name || newEmail != user.email
            if (infoChanged) {
                when (val result = repository.updateUser(user.id, newName, newEmail)) {
                    is Resource.Error -> {
                        _userMessage.emit(result.message)
                        return@launch
                    }
                    is Resource.Success -> Unit
                }
            }

            if (newRole != user.role) {
                when (val roleResult = repository.updateRole(user.id, newRole)) {
                    is Resource.Error -> {
                        _userMessage.emit(roleResult.message)
                        loadUsers()
                        return@launch
                    }
                    is Resource.Success -> Unit
                }
            }

            if (infoChanged || newRole != user.role) {
                _userMessage.emit("Data pengguna berhasil diperbarui")
                loadUsers()
            }
        }
    }

    fun toggleStatus(user: MasterUser) {
        val newStatus = if (user.isActive) "BLOCKED" else "ACTIVE"
        viewModelScope.launch {
            when (val result = repository.updateStatus(user.id, newStatus)) {
                is Resource.Success -> {
                    val statusLabel = if (result.data.isActive) "diaktifkan" else "diblokir"
                    _userMessage.emit("Akun ${user.name} berhasil $statusLabel")
                    loadUsers()
                }
                is Resource.Error -> _userMessage.emit(result.message)
            }
        }
    }

    fun resetPassword(id: Int, newPassword: String) {
        viewModelScope.launch {
            when (val result = repository.resetPassword(id, newPassword)) {
                is Resource.Success -> _userMessage.emit("Password berhasil direset")
                is Resource.Error -> _userMessage.emit(result.message)
            }
        }
    }

    fun deleteUser(id: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteUser(id)) {
                is Resource.Success -> {
                    _userMessage.emit("Pengguna berhasil dihapus")
                    loadUsers()
                }
                is Resource.Error -> _userMessage.emit(result.message)
            }
        }
    }
}
