package id.sch.santaagnes.sim.ui.admin.master.teachers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.MataPelajaranDto
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.ClassRepository
import id.sch.santaagnes.sim.data.repository.TeacherRepository
import id.sch.santaagnes.sim.domain.model.ClassFilter
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterSemester
import id.sch.santaagnes.sim.domain.model.MasterTeacher
import id.sch.santaagnes.sim.domain.model.MasterTeachingAssignment
import id.sch.santaagnes.sim.domain.model.TeacherFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TeacherSectionMode {
    TEACHERS,
    ASSIGNMENTS
}

data class AdminTeacherUiState(
    val mode: TeacherSectionMode = TeacherSectionMode.TEACHERS,
    val teachers: List<MasterTeacher> = emptyList(),
    val allTeachersLookup: List<MasterTeacher> = emptyList(),
    val assignments: List<MasterTeachingAssignment> = emptyList(),
    val subjects: List<MataPelajaranDto> = emptyList(),
    val classes: List<MasterClassGroup> = emptyList(),
    val activeSemester: MasterSemester? = null,
    val activePeriodText: String? = null,
    val totalTeachers: Int = 0,
    val homeroomCount: Int = 0,
    val regularCount: Int = 0,
    val searchQuery: String = "",
    val homeroomFilter: Boolean? = null,
    val selectedTeacherForAssignments: MasterTeacher? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AdminTeacherViewModel @Inject constructor(
    private val repository: TeacherRepository,
    private val classRepository: ClassRepository,
    private val academicYearRepository: AcademicYearRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminTeacherUiState())
    val uiState: StateFlow<AdminTeacherUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    private var rawAssignments: List<MasterTeachingAssignment> = emptyList()

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            val semRes = academicYearRepository.getActiveSemester()
            val sem = (semRes as? Resource.Success)?.data
            val periodText = sem?.let { "${it.label} · ${it.academicYearName ?: "-"}" }
            _uiState.value = _uiState.value.copy(
                activeSemester = sem,
                activePeriodText = periodText
            )

            loadTeachers()

            val subRes = repository.getSubjects()
            if (subRes is Resource.Success) {
                _uiState.value = _uiState.value.copy(subjects = subRes.data)
            }
            val clsRes = classRepository.getClasses(
                page = 1,
                limit = 100,
                filter = ClassFilter(academicYearId = sem?.academicYearId)
            )
            if (clsRes is Resource.Success) {
                _uiState.value = _uiState.value.copy(classes = clsRes.data.first)
            }
        }
    }

    fun setMode(mode: TeacherSectionMode, teacher: MasterTeacher? = _uiState.value.selectedTeacherForAssignments) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            selectedTeacherForAssignments = if (mode == TeacherSectionMode.ASSIGNMENTS) teacher else null,
            errorMessage = null
        )
        if (mode == TeacherSectionMode.TEACHERS) {
            loadTeachers()
        } else {
            loadAssignments()
        }
    }

    fun clearTeacherAssignmentFilter() {
        _uiState.value = _uiState.value.copy(selectedTeacherForAssignments = null)
        loadAssignments()
    }

    fun loadTeachers() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            val filter = TeacherFilter(
                search = state.searchQuery.takeIf { it.isNotBlank() },
                isHomeroomTeacher = state.homeroomFilter
            )
            when (val res = repository.getTeachers(page = 1, limit = 100, filter = filter)) {
                is Resource.Success -> {
                    val list = res.data.first
                    val homeroom = list.count { it.isHomeroomTeacher }
                    val regular = list.size - homeroom
                    val lookup = if (state.homeroomFilter == null && state.searchQuery.isBlank()) {
                        list
                    } else {
                        _uiState.value.allTeachersLookup.ifEmpty { list }
                    }
                    _uiState.value = _uiState.value.copy(
                        teachers = list,
                        allTeachersLookup = lookup,
                        totalTeachers = res.data.second,
                        homeroomCount = homeroom,
                        regularCount = regular,
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

    fun loadAssignments() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            val res = repository.getTeachingAssignments(
                semesterId = state.activeSemester?.id,
                teacherId = state.selectedTeacherForAssignments?.id
            )
            when (res) {
                is Resource.Success -> {
                    rawAssignments = res.data.first
                    _uiState.value = _uiState.value.copy(
                        assignments = filterAssignments(rawAssignments, state.searchQuery),
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

    private fun filterAssignments(
        list: List<MasterTeachingAssignment>,
        query: String
    ): List<MasterTeachingAssignment> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter {
            it.teacherName.lowercase().contains(q) ||
                it.subjectName.lowercase().contains(q) ||
                it.className.lowercase().contains(q)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (_uiState.value.mode == TeacherSectionMode.TEACHERS) {
            loadTeachers()
        } else {
            _uiState.value = _uiState.value.copy(
                assignments = filterAssignments(rawAssignments, query)
            )
        }
    }

    fun setHomeroomFilter(isHomeroom: Boolean?) {
        val next = if (_uiState.value.homeroomFilter == isHomeroom && isHomeroom != null) null else isHomeroom
        _uiState.value = _uiState.value.copy(homeroomFilter = next)
        if (_uiState.value.mode == TeacherSectionMode.TEACHERS) {
            loadTeachers()
        }
    }

    fun createTeacher(name: String, email: String, password: String, employeeNumber: String) {
        viewModelScope.launch {
            when (val res = repository.createTeacher(name, email, password, employeeNumber)) {
                is Resource.Success -> {
                    _message.emit("Guru ${res.data.name} berhasil ditambahkan")
                    loadTeachers()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun updateTeacher(id: Int, name: String, email: String, employeeNumber: String) {
        viewModelScope.launch {
            when (val res = repository.updateTeacher(id, name, email, employeeNumber)) {
                is Resource.Success -> {
                    _message.emit("Data guru berhasil diperbarui")
                    loadTeachers()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun deleteTeacher(id: Int) {
        viewModelScope.launch {
            when (val res = repository.deleteTeacher(id)) {
                is Resource.Success -> {
                    _message.emit("Data guru berhasil dihapus")
                    loadTeachers()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun createTeachingAssignment(teacherId: Int, subjectId: Int, classGroupId: Int) {
        val semesterId = _uiState.value.activeSemester?.id
        if (semesterId == null) {
            viewModelScope.launch {
                _message.emit("Aktifkan semester terlebih dahulu di menu Tahun Ajaran & Semester.")
            }
            return
        }
        viewModelScope.launch {
            when (val res = repository.createTeachingAssignment(teacherId, subjectId, classGroupId, semesterId)) {
                is Resource.Success -> {
                    _message.emit("Penugasan mengajar berhasil ditambahkan")
                    loadAssignments()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun updateTeachingAssignmentTeacher(assignmentId: Int, teacherId: Int) {
        viewModelScope.launch {
            when (val res = repository.updateTeachingAssignmentTeacher(assignmentId, teacherId)) {
                is Resource.Success -> {
                    _message.emit("Guru pengajar berhasil diganti")
                    loadAssignments()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }

    fun deleteTeachingAssignment(assignmentId: Int) {
        viewModelScope.launch {
            when (val res = repository.deleteTeachingAssignment(assignmentId)) {
                is Resource.Success -> {
                    _message.emit("Penugasan mengajar berhasil dihapus")
                    loadAssignments()
                }
                is Resource.Error -> _message.emit(res.message)
            }
        }
    }
}
