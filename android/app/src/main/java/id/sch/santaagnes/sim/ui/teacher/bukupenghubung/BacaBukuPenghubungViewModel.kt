package id.sch.santaagnes.sim.ui.bukupenghubung

import id.sch.santaagnes.sim.core.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BacaBukuPenghubungUiState(
    val siswa: List<SiswaBacaBukuDto> = emptyList(),
    val selectedStudentId: Int? = null,
    val riwayat: RiwayatBukuPenghubungDto? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BacaBukuPenghubungViewModel @Inject constructor(
    private val api: BacaBukuPenghubungApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BacaBukuPenghubungUiState()
    )

    val uiState = _uiState.asStateFlow()

    private var catatanJob: Job? = null

    fun loadSiswa() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.getSiswa()

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil siswa"
                    )
                }

                val siswa = response.data.orEmpty()

                val selectedId = _uiState.value.selectedStudentId
                    ?.takeIf { id -> siswa.any { it.id == id } }
                    ?: siswa.firstOrNull()?.id

                _uiState.value = _uiState.value.copy(
                    siswa = siswa,
                    selectedStudentId = selectedId,
                    riwayat = null,
                    loading = false
                )

                if (selectedId != null) {
                    pilihSiswa(selectedId)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.userMessage("Terjadi kesalahan")
                )
            }
        }
    }

    fun pilihSiswa(studentId: Int) {
        if (_uiState.value.siswa.none { it.id == studentId }) return

        catatanJob?.cancel()

        _uiState.value = _uiState.value.copy(
            selectedStudentId = studentId,
            riwayat = null,
            loading = true,
            error = null
        )

        catatanJob = viewModelScope.launch {
            try {
                val response = api.getCatatan(studentId)

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil catatan"
                    )
                }

                if (_uiState.value.selectedStudentId != studentId) {
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    riwayat = response.data,
                    loading = false
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.selectedStudentId == studentId) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = e.userMessage("Gagal mengambil catatan")
                    )
                }
            }
        }
    }

    fun retry() {
        val studentId = _uiState.value.selectedStudentId

        if (studentId != null && _uiState.value.siswa.isNotEmpty()) {
            pilihSiswa(studentId)
        } else {
            loadSiswa()
        }
    }
}
