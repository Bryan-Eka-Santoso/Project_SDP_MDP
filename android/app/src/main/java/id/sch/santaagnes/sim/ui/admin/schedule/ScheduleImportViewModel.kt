package id.sch.santaagnes.sim.ui.admin.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleImportUiState(
    val fileUri: String? = null,
    val fileName: String? = null,
    val isUploading: Boolean = false,
    // jumlah slot yang berhasil diimpor; null selama belum ada impor sukses
    val importedCount: Int? = null,
    // alasan gagal: satu baris per kesalahan (nomor baris + alasan) untuk impor yang ditolak
    val errorMessage: String? = null
) {
    val canImport: Boolean get() = fileUri != null && !isUploading
}

@HiltViewModel
class ScheduleImportViewModel @Inject constructor(
    private val repository: AdminScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleImportUiState())
    val uiState: StateFlow<ScheduleImportUiState> = _uiState.asStateFlow()

    fun onFilePicked(uri: String) {
        // Hasil impor sebelumnya dihapus: berkas baru berarti percobaan baru.
        _uiState.update { ScheduleImportUiState(fileUri = uri) }
        viewModelScope.launch {
            val name = repository.fileName(uri)
            _uiState.update { if (it.fileUri == uri) it.copy(fileName = name) else it }
        }
    }

    fun import() {
        val uri = _uiState.value.fileUri ?: return
        if (_uiState.value.isUploading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null, importedCount = null) }
            when (val result = repository.importSlots(uri)) {
                is Resource.Success -> _uiState.update {
                    it.copy(isUploading = false, importedCount = result.data.imported)
                }
                is Resource.Error -> _uiState.update { it.copy(isUploading = false, errorMessage = result.message) }
            }
        }
    }
}
