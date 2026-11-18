package id.sch.santaagnes.sim.ui.admin.master.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.ClassRepository
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.data.repository.TeacherRepository
import id.sch.santaagnes.sim.domain.model.ClassFilter
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterClassMembersPage
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.domain.model.MasterTeacher
import id.sch.santaagnes.sim.domain.model.StudentFilter
import id.sch.santaagnes.sim.domain.model.TeacherFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminClassUiState(
    val classes: List<MasterClassGroup> = emptyList(),
    val academicYears: List<MasterAcademicYear> = emptyList(),
    val teachers: List<MasterTeacher> = emptyList(),
    val studentsLookup: List<MasterStudent> = emptyList(),
    val selectedMembersPage: MasterClassMembersPage? = null,
    val isMembersLoading: Boolean = false,
    val totalClasses: Int = 0,
    val withHomeroomCount: Int = 0,
    val withoutHomeroomCount: Int = 0,
    val activePeriodText: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentFilter: ClassFilter = ClassFilter()
)

@HiltViewModel
class AdminClassViewModel @Inject constructor(
    private val repository: ClassRepository,
    private val academicYearRepository: AcademicYearRepository,
    private val teacherRepository: TeacherRepository,
    private val studentRepository: StudentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminClassUiState())
    val uiState: StateFlow<AdminClassUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        loadLookups()
        loadClasses()
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
            val yearRes = academicYearRepository.getAcademicYears()
            if (yearRes is Resource.Success) {
                _uiState.value = _uiState.value.copy(academicYears = yearRes.data)
            }
            val teacherRes = teacherRepository.getTeachers(page = 1, limit = 100, filter = TeacherFilter())
            if (teacherRes is Resource.Success) {
                _uiState.value = _uiState.value.copy(teachers = teacherRes.data.first)
            }
            val studentRes = studentRepository.getStudents(page = 1, limit = 100, filter = StudentFilter())
            if (studentRes is Resource.Success) {
                _uiState.value = _uiState.value.copy(studentsLookup = studentRes.data.first)
            }
        }
    }

    fun loadClasses(filter: ClassFilter = _uiState.value.currentFilter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                currentFilter = filter
            )
            when (val res = repository.getClasses(page = 1, limit = 100, filter = filter)) {
                is Resource.Success -> {
                    val list = res.data.first
                    val withHomeroom = list.count { it.homeroomTeacherId != null }
                    _uiState.value = _uiState.value.copy(
                        classes = list,
                        totalClasses = res.data.second,
                        withHomeroomCount = withHomeroom,
                        withoutHomeroomCount = list.size - withHomeroom,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = res.message
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        loadClasses(_uiState.value.currentFilter.copy(search = query.trim()))
    }

    fun setYearFilter(academicYearId: Int?) {
        loadClasses(_uiState.value.currentFilter.copy(academicYearId = academicYearId))
    }

    fun createClass(
        academicYearId: Int,
        name: String,
        gradeLevel: String,
        capacity: Int,
        homeroomTeacherId: Int?
    ) {
        viewModelScope.launch {
            when (val res = repository.createClass(academicYearId, name, gradeLevel, capacity, homeroomTeacherId)) {
                is Resource.Success -> {
                    _message.emit("Kelas ${res.data.name} berhasil ditambahkan")
                    loadClasses()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun updateClass(id: Int, name: String, gradeLevel: String, capacity: Int) {
        viewModelScope.launch {
            when (val res = repository.updateClass(id, name, gradeLevel, capacity)) {
                is Resource.Success -> {
                    _message.emit("Data kelas berhasil diperbarui")
                    loadClasses()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun setHomeroomTeacher(classId: Int, homeroomTeacherId: Int?) {
        viewModelScope.launch {
            when (val res = repository.setHomeroomTeacher(classId, homeroomTeacherId)) {
                is Resource.Success -> {
                    val msg = if (homeroomTeacherId == null) {
                        "Wali kelas berhasil dilepas"
                    } else {
                        "Wali kelas ${res.data.name} berhasil diatur"
                    }
                    _message.emit(msg)
                    loadClasses()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun deleteClass(id: Int) {
        viewModelScope.launch {
            when (val res = repository.deleteClass(id)) {
                is Resource.Success -> {
                    _message.emit("Kelas berhasil dihapus")
                    loadClasses()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun loadClassMembers(classId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMembersLoading = true)
            when (val res = repository.getClassMembers(classId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedMembersPage = res.data,
                        isMembersLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isMembersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }

    fun addClassMember(classId: Int, studentId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMembersLoading = true)
            when (val res = repository.addClassMembers(classId, listOf(studentId))) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedMembersPage = res.data,
                        isMembersLoading = false
                    )
                    _message.emit("Siswa berhasil ditempatkan di kelas")
                    loadClasses()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isMembersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }

    fun moveClassMember(sourceClassId: Int, studentId: Int, targetClassId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMembersLoading = true)
            when (val res = repository.moveClassMember(sourceClassId, studentId, targetClassId)) {
                is Resource.Success -> {
                    _message.emit("Siswa berhasil dipindahkan")
                    loadClassMembers(sourceClassId)
                    loadClasses()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isMembersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }

    fun removeClassMember(classId: Int, studentId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMembersLoading = true)
            when (val res = repository.removeClassMember(classId, studentId)) {
                is Resource.Success -> {
                    _message.emit("Siswa dikeluarkan dari kelas")
                    loadClassMembers(classId)
                    loadClasses()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(isMembersLoading = false)
                    _message.emit(res.message)
                }
            }
        }
    }
}
