package id.sch.santaagnes.sim.ui.admin.master.periods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterSemester
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminAcademicYearUiState(
    val years: List<MasterAcademicYear> = emptyList(),
    val semestersForYear: List<MasterSemester> = emptyList(),
    val activePeriodText: String? = null,
    val isLoading: Boolean = false,
    val isSemestersLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AdminAcademicYearViewModel @Inject constructor(
    private val repository: AcademicYearRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminAcademicYearUiState())
    val uiState: StateFlow<AdminAcademicYearUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        loadYears()
    }

    fun loadYears() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val semRes = repository.getActiveSemester()
            val periodText = (semRes as? Resource.Success)?.data?.let {
                "${it.label} · ${it.academicYearName ?: "-"}"
            }

            when (val res = repository.getAcademicYears()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        years = res.data,
                        activePeriodText = periodText,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        activePeriodText = periodText,
                        isLoading = false,
                        errorMessage = res.message
                    )
                }
            }
        }
    }

    fun createYear(name: String, startDate: String, endDate: String, isActive: Boolean) {
        viewModelScope.launch {
            when (val res = repository.createAcademicYear(name, startDate, endDate, isActive)) {
                is Resource.Success -> {
                    _message.emit("Tahun ajaran ${res.data.name} berhasil ditambahkan")
                    loadYears()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun updateYear(id: Int, name: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            when (val res = repository.updateAcademicYear(id, name, startDate, endDate)) {
                is Resource.Success -> {
                    _message.emit("Tahun ajaran berhasil diperbarui")
                    loadYears()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun setActiveYear(id: Int) {
        viewModelScope.launch {
            when (val res = repository.setActiveAcademicYear(id)) {
                is Resource.Success -> {
                    _message.emit("Tahun ajaran ${res.data.name} kini aktif")
                    loadYears()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun deleteYear(id: Int) {
        viewModelScope.launch {
            when (val res = repository.deleteAcademicYear(id)) {
                is Resource.Success -> {
                    _message.emit("Tahun ajaran berhasil dihapus")
                    loadYears()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun loadSemesters(yearId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSemestersLoading = true)
            when (val res = repository.getAcademicYearDetail(yearId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        semestersForYear = res.data.semesters,
                        isSemestersLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSemestersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }

    fun createSemester(
        yearId: Int,
        type: String,
        startDate: String,
        endDate: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSemestersLoading = true)
            when (val res = repository.createSemester(yearId, type, startDate, endDate, isActive)) {
                is Resource.Success -> {
                    _message.emit("Semester berhasil ditambahkan")
                    loadSemesters(yearId)
                    loadYears()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSemestersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }

    fun setActiveSemester(yearId: Int, semesterId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSemestersLoading = true)
            when (val res = repository.setActiveSemester(semesterId)) {
                is Resource.Success -> {
                    _message.emit("${res.data.label} berhasil diaktifkan")
                    loadSemesters(yearId)
                    loadYears()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSemestersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }

    fun deleteSemester(yearId: Int, semesterId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSemestersLoading = true)
            when (val res = repository.deleteSemester(semesterId)) {
                is Resource.Success -> {
                    _message.emit("Semester berhasil dihapus")
                    loadSemesters(yearId)
                    loadYears()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isSemestersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }
}
