package id.sch.santaagnes.sim.ui.admin.elective

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.ElectiveRepository
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.PackageSubject
import id.sch.santaagnes.sim.domain.model.SubjectPackage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectPackageUiState(
    val packages: List<SubjectPackage> = emptyList(),
    val years: List<MasterAcademicYear> = emptyList(),
    val subjects: List<PackageSubject> = emptyList(),
    val selectedYearId: Int? = null,
    val activePeriodText: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

/** Mengelola master paket mata pelajaran pilihan per tahun ajaran (F-MPP-03). */
@HiltViewModel
class SubjectPackageViewModel @Inject constructor(
    private val repository: ElectiveRepository,
    private val academicYearRepository: AcademicYearRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectPackageUiState())
    val uiState: StateFlow<SubjectPackageUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val yearsRes = academicYearRepository.getAcademicYears(page = 1, limit = 50)
            val years = (yearsRes as? Resource.Success)?.data.orEmpty()
            val activeYear = years.firstOrNull { it.isActive } ?: years.firstOrNull()
            val resolvedYearId = _uiState.value.selectedYearId ?: activeYear?.id
            val periodText = activeYear?.let {
                if (it.isActive) "Tahun Ajaran ${it.name} (Aktif)" else "Tahun Ajaran ${it.name}"
            }

            val subjectsRes = repository.getSubjects()
            val subjects = (subjectsRes as? Resource.Success)?.data.orEmpty()

            _uiState.update {
                it.copy(
                    years = years,
                    subjects = subjects,
                    selectedYearId = resolvedYearId,
                    activePeriodText = periodText
                )
            }
            loadPackages(resolvedYearId, _uiState.value.searchQuery)
        }
    }

    fun setAcademicYearFilter(yearId: Int) {
        _uiState.update { it.copy(selectedYearId = yearId) }
        loadPackages(yearId, _uiState.value.searchQuery)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadPackages(_uiState.value.selectedYearId, query)
    }

    fun loadPackages(
        yearId: Int? = _uiState.value.selectedYearId,
        search: String = _uiState.value.searchQuery
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getSubjectPackages(academicYearId = yearId, search = search)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(packages = res.data, isLoading = false) }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    _message.emit(res.message)
                }
            }
        }
    }

    fun createPackage(
        academicYearId: Int,
        name: String,
        capacity: Int,
        subjectIds: List<Int>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val res = repository.createSubjectPackage(academicYearId, name, capacity, subjectIds)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, selectedYearId = academicYearId) }
                    _message.emit("Paket \"${res.data.name}\" berhasil ditambahkan")
                    loadPackages(academicYearId, _uiState.value.searchQuery)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(res.message)
                }
            }
        }
    }

    fun updatePackage(
        id: Int,
        name: String,
        capacity: Int,
        subjectIds: List<Int>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val res = repository.updateSubjectPackage(id, name, capacity, subjectIds)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit("Paket \"${res.data.name}\" berhasil diperbarui")
                    loadPackages(_uiState.value.selectedYearId, _uiState.value.searchQuery)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(res.message)
                }
            }
        }
    }

    fun deletePackage(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val res = repository.deleteSubjectPackage(id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit("Paket mata pelajaran berhasil dihapus")
                    loadPackages(_uiState.value.selectedYearId, _uiState.value.searchQuery)
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(res.message)
                }
            }
        }
    }
}
