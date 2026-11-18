package id.sch.santaagnes.sim.ui.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.AdminOverviewRepository
import id.sch.santaagnes.sim.domain.model.AdminOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDashboardUiState(
    val adminName: String? = null,
    val activePeriodText: String? = null,
    val overview: AdminOverview = AdminOverview(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val overviewRepository: AdminOverviewRepository,
    private val academicYearRepository: AcademicYearRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val name = sessionManager.getUserName()
            _uiState.update { it.copy(adminName = name) }
        }
        refresh()
    }

    /** Muat ulang jumlah data dan periode aktif; dipanggil saat layar tampil. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val periodResult = academicYearRepository.getActiveSemester()
            val periodText = if (periodResult is Resource.Success) {
                val sem = periodResult.data
                val year = sem.academicYearName ?: "-"
                "${sem.label} · $year"
            } else {
                null
            }

            when (val result = overviewRepository.overview()) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        overview = result.data,
                        activePeriodText = periodText ?: it.activePeriodText,
                        isLoading = false
                    )
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        activePeriodText = periodText ?: it.activePeriodText,
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }
}
