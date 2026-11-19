package id.sch.santaagnes.sim.ui.admin.master

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.data.repository.TeacherRepository
import id.sch.santaagnes.sim.domain.model.ImportRowError
import id.sch.santaagnes.sim.domain.model.MasterImportResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MasterImportType {
    STUDENT,
    TEACHER
}

data class MasterImportUiState(
    val type: MasterImportType = MasterImportType.STUDENT,
    val fileUri: String? = null,
    val fileName: String? = null,
    val isUploading: Boolean = false,
    val result: MasterImportResult? = null,
    val errorMessage: String? = null,
    val importErrors: List<ImportRowError> = emptyList()
) {
    val errorRowCount: Int get() = importErrors.mapNotNull { it.row }.distinct().size
    val canImport: Boolean get() = fileUri != null && !isUploading
}

/** Mengelola unduh template dan unggah Excel untuk master siswa (F-MST-04) maupun guru (F-MST-05). */
@HiltViewModel
class MasterImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studentRepository: StudentRepository,
    private val teacherRepository: TeacherRepository
) : ViewModel() {

    val importType: MasterImportType = savedStateHandle
        .get<String>(ARG_IMPORT_TYPE)
        ?.let { raw -> runCatching { MasterImportType.valueOf(raw) }.getOrNull() }
        ?: MasterImportType.STUDENT

    private val _uiState = MutableStateFlow(MasterImportUiState(type = importType))
    val uiState: StateFlow<MasterImportUiState> = _uiState.asStateFlow()

    private val _templateSaved = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val templateSaved: SharedFlow<Boolean> = _templateSaved.asSharedFlow()

    fun onTemplateDestinationPicked(uri: String) {
        viewModelScope.launch {
            val saved = when (importType) {
                MasterImportType.STUDENT -> studentRepository.saveImportTemplate(uri)
                MasterImportType.TEACHER -> teacherRepository.saveImportTemplate(uri)
            }
            _templateSaved.emit(saved)
        }
    }

    fun onFilePicked(uri: String) {
        _uiState.update { MasterImportUiState(type = importType, fileUri = uri) }
        viewModelScope.launch {
            val name = when (importType) {
                MasterImportType.STUDENT -> studentRepository.fileName(uri)
                MasterImportType.TEACHER -> teacherRepository.fileName(uri)
            }
            _uiState.update { if (it.fileUri == uri) it.copy(fileName = name) else it }
        }
    }

    fun import() {
        val uri = _uiState.value.fileUri ?: return
        if (_uiState.value.isUploading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isUploading = true, errorMessage = null, importErrors = emptyList(), result = null)
            }
            val resource = when (importType) {
                MasterImportType.STUDENT -> studentRepository.importStudents(uri)
                MasterImportType.TEACHER -> teacherRepository.importTeachers(uri)
            }
            when (resource) {
                is Resource.Success -> _uiState.update {
                    it.copy(isUploading = false, result = resource.data)
                }
                is Resource.Error -> _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = resource.message,
                        importErrors = resource.importErrors
                    )
                }
            }
        }
    }

    companion object {
        const val ARG_IMPORT_TYPE = "importType"
    }
}
