package id.sch.santaagnes.sim.ui.admin.master.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.CreateStudentRequest
import id.sch.santaagnes.sim.data.remote.UpdateStudentRequest
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.domain.model.MasterStudent
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
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val inactiveCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentFilter: StudentFilter = StudentFilter()
)

@HiltViewModel
class AdminStudentViewModel @Inject constructor(
    private val repository: StudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminStudentUiState())
    val uiState: StateFlow<AdminStudentUiState> = _uiState.asStateFlow()

    private val _studentMessage = MutableSharedFlow<String>()
    val studentMessage: SharedFlow<String> = _studentMessage.asSharedFlow()

    init {
        loadStudents()
    }

    fun loadStudents(filter: StudentFilter = _uiState.value.currentFilter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                currentFilter = filter
            )

            when (val result = repository.getStudents(page = 1, limit = 50, filter = filter)) {
                is Resource.Success -> {
                    val students = result.data.first
                    val total = result.data.second
                    val active = students.count { it.isActive }
                    val inactive = students.size - active

                    _uiState.value = _uiState.value.copy(
                        students = students,
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

    fun setSearchQuery(query: String) {
        val newFilter = _uiState.value.currentFilter.copy(search = query.trim())
        loadStudents(newFilter)
    }

    fun setClassFilter(classGroupId: Int?) {
        val newFilter = _uiState.value.currentFilter.copy(classGroupId = classGroupId)
        loadStudents(newFilter)
    }

    fun createStudent(
        name: String,
        email: String,
        password: String,
        nis: String,
        phone: String?
    ) {
        viewModelScope.launch {
            val req = CreateStudentRequest(
                email = email,
                password = password,
                name = name,
                nis = nis,
                phone = phone?.takeIf { it.isNotBlank() }
            )
            when (val result = repository.createStudent(req)) {
                is Resource.Success -> {
                    _studentMessage.emit("Siswa ${result.data.name} berhasil ditambahkan")
                    loadStudents()
                }
                is Resource.Error -> {
                    _studentMessage.emit(result.message)
                }
            }
        }
    }

    fun updateStudent(
        id: Int,
        name: String,
        nis: String,
        phone: String?
    ) {
        viewModelScope.launch {
            val req = UpdateStudentRequest(
                name = name,
                nis = nis,
                phone = phone
            )
            when (val result = repository.updateStudent(id, req)) {
                is Resource.Success -> {
                    _studentMessage.emit("Data siswa berhasil diperbarui")
                    loadStudents()
                }
                is Resource.Error -> {
                    _studentMessage.emit(result.message)
                }
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
                is Resource.Error -> {
                    _studentMessage.emit(result.message)
                }
            }
        }
    }

    fun linkGuardian(studentId: Int, guardianId: Int, relationship: String) {
        viewModelScope.launch {
            when (val result = repository.linkGuardian(studentId, guardianId, relationship)) {
                is Resource.Success -> {
                    _studentMessage.emit("Relasi wali murid berhasil ditambahkan")
                    loadStudents()
                }
                is Resource.Error -> {
                    _studentMessage.emit(result.message)
                }
            }
        }
    }
}
