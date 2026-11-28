package id.sch.santaagnes.sim.ui.pelanggaran

import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.core.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RiwayatPelanggaranUiState(
    val siswa: List<SiswaRiwayatDto> = emptyList(),
    val selectedStudentId: Int? = null,
    val riwayat: RiwayatPelanggaranDto? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RiwayatPelanggaranViewModel @Inject constructor(
    private val api: RiwayatPelanggaranApi,
    private val session: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RiwayatPelanggaranUiState()
    )

    val uiState = _uiState.asStateFlow()

    fun loadData() {
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

                _uiState.value = _uiState.value.copy(
                    siswa = siswa,
                    loading = false
                )

                // Wali murid mengikuti anak aktif dari Beranda; selain itu buka siswa pertama.
                if (siswa.isNotEmpty()) {
                    val selected = _uiState.value.selectedStudentId
                        ?: if (session.getRole() == "GUARDIAN") session.getActiveStudentId() else null
                    val id = if (siswa.any { it.id == selected }) {
                        selected!!
                    } else {
                        siswa.first().id
                    }

                    pilihSiswa(id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        selectedStudentId = null,
                        riwayat = null
                    )
                }

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
        viewModelScope.launch {
            if (session.getRole() == "GUARDIAN") session.saveActiveStudentId(studentId)
        }

        _uiState.value = _uiState.value.copy(
            selectedStudentId = studentId,
            riwayat = null,
            loading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val response = api.getRiwayat(studentId)

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil riwayat"
                    )
                }

                // Abaikan respons lama jika siswa berganti.
                if (_uiState.value.selectedStudentId != studentId) {
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    riwayat = response.data,
                    loading = false
                )

            } catch (e: Exception) {
                if (_uiState.value.selectedStudentId != studentId) {
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.userMessage("Gagal mengambil riwayat")
                )
            }
        }
    }

    fun retry() {
        val id = _uiState.value.selectedStudentId

        if (id != null && _uiState.value.siswa.any { it.id == id }) {
            pilihSiswa(id)
        } else {
            loadData()
        }
    }
}