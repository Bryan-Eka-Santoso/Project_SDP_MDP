package id.sch.santaagnes.sim.ui.admin.master.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateStudentRequest
import id.sch.santaagnes.sim.data.remote.UpdateStudentRequest
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.ClassRepository
import id.sch.santaagnes.sim.data.repository.GuardianRepository
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.domain.model.ClassFilter
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterGuardian
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.domain.model.MasterStudentGuardian
import id.sch.santaagnes.sim.domain.model.StudentFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminStudentUiState(
    val students: List<MasterStudent> = emptyList(),
    val allStudents: List<MasterStudent> = emptyList(),
    val classes: List<MasterClassGroup> = emptyList(),
    val guardians: List<MasterGuardian> = emptyList(),
    val selectedStudentGuardians: List<MasterStudentGuardian> = emptyList(),
    val isGuardianLoading: Boolean = false,
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val inactiveCount: Int = 0,
    val statusFilter: String? = null,
    val activePeriodText: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentFilter: StudentFilter = StudentFilter()
)

@HiltViewModel
class AdminStudentViewModel @Inject constructor(
    private val repository: StudentRepository,
    private val classRepository: ClassRepository,
    private val guardianRepository: GuardianRepository,
    private val academicYearRepository: AcademicYearRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminStudentUiState())
    val uiState: StateFlow<AdminStudentUiState> = _uiState.asStateFlow()

    private val _studentMessage = MutableSharedFlow<String>()
    val studentMessage: SharedFlow<String> = _studentMessage.asSharedFlow()

    init {
        loadLookups()
        loadStudents()
    }

    private fun loadLookups() {
        viewModelScope.launch {
            val semRes = academicYearRepository.getActiveSemester()
            if (semRes is Resource.Success) {
                val sem = semRes.data
                _uiState.value = _uiState.value.copy(
                    activePeriodText = "${sem.label} · ${sem.academicYearName ?: "-"}"
                )
            }
            val classRes = classRepository.getClasses(page = 1, limit = 100, filter = ClassFilter())
            if (classRes is Resource.Success) {
                _uiState.value = _uiState.value.copy(classes = classRes.data.first)
            }
        }
    }

    fun loadStudents(filter: StudentFilter = _uiState.value.currentFilter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                currentFilter = filter
            )

            when (val result = repository.getStudents(page = 1, limit = 100, filter = filter)) {
                is Resource.Success -> {
                    val all = result.data.first
                    val total = result.data.second
                    val active = all.count { it.isActive }
                    val inactive = all.size - active
                    val filtered = applyStatusFilter(all, _uiState.value.statusFilter)

                    _uiState.value = _uiState.value.copy(
                        allStudents = all,
                        students = filtered,
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

    private fun applyStatusFilter(list: List<MasterStudent>, status: String?): List<MasterStudent> =
        when (status) {
            "ACTIVE" -> list.filter { it.isActive }
            "BLOCKED" -> list.filter { !it.isActive }
            else -> list
        }

    fun setStatusFilter(status: String) {
        val next = if (_uiState.value.statusFilter == status) null else status
        val filtered = applyStatusFilter(_uiState.value.allStudents, next)
        _uiState.value = _uiState.value.copy(statusFilter = next, students = filtered)
    }

    fun setSearchQuery(query: String) {
        loadStudents(_uiState.value.currentFilter.copy(search = query.trim()))
    }

    fun setClassFilter(classGroupId: Int?) {
        loadStudents(_uiState.value.currentFilter.copy(classGroupId = classGroupId))
    }

    fun createStudent(
        name: String,
        email: String,
        password: String,
        nis: String,
        birthDate: String,
        phone: String?
    ) {
        viewModelScope.launch {
            val req = CreateStudentRequest(
                email = email,
                password = password,
                name = name,
                nis = nis,
                birthDate = birthDate,
                phone = phone?.takeIf { it.isNotBlank() }
            )
            when (val result = repository.createStudent(req)) {
                is Resource.Success -> {
                    _studentMessage.emit("Siswa ${result.data.name} berhasil ditambahkan")
                    loadStudents()
                }
                is Resource.Error -> _studentMessage.emit(result.message)
            }
        }
    }

    fun updateStudent(
        id: Int,
        name: String,
        email: String,
        nis: String,
        birthDate: String,
        phone: String?
    ) {
        viewModelScope.launch {
            val req = UpdateStudentRequest(
                name = name,
                email = email,
                nis = nis,
                birthDate = birthDate,
                phone = phone?.takeIf { it.isNotBlank() }
            )
            when (val result = repository.updateStudent(id, req)) {
                is Resource.Success -> {
                    _studentMessage.emit("Data siswa berhasil diperbarui")
                    loadStudents()
                }
                is Resource.Error -> _studentMessage.emit(result.message)
            }
        }
    }

    fun deleteStudent(id: Int) {
        viewModelScope.launch {
            when (val result = repository.deleteStudent(id)) {
                is Resource.Success -> {
                    _studentMessage.emit("Siswa berhasil dihapus")
                    loadStudents()
                }
                is Resource.Error -> _studentMessage.emit(result.message)
            }
        }
    }

    fun loadGuardianPanel(studentId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGuardianLoading = true)
            val detailRes = repository.getStudentDetail(studentId)
            val allRes = guardianRepository.getGuardians()
            val linked = (detailRes as? Resource.Success)?.data?.guardians.orEmpty()
            val all = (allRes as? Resource.Success)?.data.orEmpty()
            if (detailRes is Resource.Error) _studentMessage.emit(detailRes.message)
            if (allRes is Resource.Error) _studentMessage.emit(allRes.message)
            _uiState.value = _uiState.value.copy(
                selectedStudentGuardians = linked,
                guardians = all,
                isGuardianLoading = false
            )
        }
    }

    fun linkGuardian(studentId: Int, guardianId: Int, relationship: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGuardianLoading = true)
            when (val result = repository.linkGuardian(studentId, guardianId, relationship)) {
                is Resource.Success -> {
                    _studentMessage.emit("Wali murid berhasil dihubungkan")
                    loadGuardianPanel(studentId)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isGuardianLoading = false)
                    _studentMessage.emit(result.message)
                }
            }
        }
    }

    fun createAndLinkGuardian(
        studentId: Int,
        name: String,
        email: String,
        password: String,
        phone: String,
        relationship: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGuardianLoading = true)
            when (val created = guardianRepository.createGuardian(name, email, password, phone)) {
                is Resource.Success -> {
                    when (val linked = repository.linkGuardian(studentId, created.data.id, relationship)) {
                        is Resource.Success -> {
                            _studentMessage.emit("Wali murid ${created.data.name} berhasil dibuat dan dihubungkan")
                            loadGuardianPanel(studentId)
                        }
                        is Resource.Error -> {
                            _uiState.value = _uiState.value.copy(isGuardianLoading = false)
                            _studentMessage.emit(linked.message)
                        }
                    }
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isGuardianLoading = false)
                    _studentMessage.emit(created.message)
                }
            }
        }
    }

    fun unlinkGuardian(studentId: Int, guardianId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGuardianLoading = true)
            when (val result = repository.unlinkGuardian(studentId, guardianId)) {
                is Resource.Success -> {
                    _studentMessage.emit("Relasi wali murid dihapus")
                    loadGuardianPanel(studentId)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isGuardianLoading = false)
                    _studentMessage.emit(result.message)
                }
            }
        }
    }
}
