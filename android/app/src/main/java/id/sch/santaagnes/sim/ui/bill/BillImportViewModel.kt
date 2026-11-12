package id.sch.santaagnes.sim.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.BillRepository
import id.sch.santaagnes.sim.domain.model.BillImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillImportUiState(
    val fileUri: String? = null,
    val fileName: String? = null,
    val isUploading: Boolean = false,
    // ringkasan impor sukses; null selama belum ada impor sukses
    val result: BillImportResult? = null,
    // alasan gagal: satu baris per kesalahan (nomor baris, kolom, alasan) untuk impor yang ditolak
    val errorMessage: String? = null
) {
    val canImport: Boolean get() = fileUri != null && !isUploading
}

/** Unggah rekap pembayaran dari yayasan (F-BYR-01); hanya admin, backend tetap memeriksanya. */
@HiltViewModel
class BillImportViewModel @Inject constructor(
    private val repository: BillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillImportUiState())
    val uiState: StateFlow<BillImportUiState> = _uiState.asStateFlow()

    fun onFilePicked(uri: String) {
        // Hasil impor sebelumnya dihapus: berkas baru berarti percobaan baru.
        _uiState.update { BillImportUiState(fileUri = uri) }
        viewModelScope.launch {
            val name = repository.fileName(uri)
            _uiState.update { if (it.fileUri == uri) it.copy(fileName = name) else it }
        }
    }

    fun import() {
        val uri = _uiState.value.fileUri ?: return
        if (_uiState.value.isUploading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null, result = null) }
            when (val result = repository.importBills(uri)) {
                is Resource.Success -> _uiState.update { it.copy(isUploading = false, result = result.data) }
                is Resource.Error -> _uiState.update { it.copy(isUploading = false, errorMessage = result.message) }
            }
        }
    }
}
