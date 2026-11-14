package id.sch.santaagnes.sim.ui.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDashboardUiState(
    val adminName: String = "Administrator",
    val totalStudents: Int = 213,
    val totalAlumni: Int = 1243,
    val totalClasses: Int = 33,
    val totalUsers: Int = 70,
    val isLoading: Boolean = false
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            val name = sessionManager.getUserName() ?: "Administrator"
            _uiState.value = _uiState.value.copy(adminName = name, isLoading = true)

            // Muat total users dan students secara aktual dari backend
            val usersResult = userRepository.getUsers(page = 1, limit = 1)
            val studentsResult = studentRepository.getStudents(page = 1, limit = 1)

            val totalUsers = if (usersResult is Resource.Success) usersResult.data.second else 70
            val totalStudents = if (studentsResult is Resource.Success) studentsResult.data.second else 213

            _uiState.value = _uiState.value.copy(
                adminName = name,
                totalUsers = totalUsers,
                totalStudents = totalStudents,
                isLoading = false
            )
        }
    }
}
