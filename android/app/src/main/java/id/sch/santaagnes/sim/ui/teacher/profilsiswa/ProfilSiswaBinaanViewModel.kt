package id.sch.santaagnes.sim.ui.teacher.profilsiswa

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

data class ProfilSiswaBinaanUiState(
    val siswa: List<SiswaBinaanDto> = emptyList(),
    val selectedStudentId: Int? = null,
    val profil: ProfilSiswaBinaanDto? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfilSiswaBinaanViewModel @Inject constructor(
    private val api: ProfilSiswaBinaanApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfilSiswaBinaanUiState()
    )

    val uiState = _uiState.asStateFlow()

    private var daftarJob: Job? = null
    private var profilJob: Job? = null

    fun loadSiswa() {
        daftarJob?.cancel()
        profilJob?.cancel()

        _uiState.value = _uiState.value.copy(
            loading = true,
            error = null,
            profil = null
        )

        daftarJob = viewModelScope.launch {
            try {
                val response = api.getSiswa()

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil daftar siswa"
                    )
                }

                val siswa = response.data.orEmpty()

                val id = _uiState.value.selectedStudentId
                    ?.takeIf { selected ->
                        siswa.any { it.id == selected }
                    }
                    ?: siswa.firstOrNull()?.id

                _uiState.value = _uiState.value.copy(
                    siswa = siswa,
                    selectedStudentId = id,
                    loading = false
                )

                if (id != null) {
                    pilihSiswa(id)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.userMessage("Gagal memuat siswa")
                )
            }
        }
    }

    fun pilihSiswa(studentId: Int) {

        if (_uiState.value.siswa.none { it.id == studentId }) {
            return
        }

        profilJob?.cancel()

        _uiState.value = _uiState.value.copy(
            selectedStudentId = studentId,
            profil = null,
            loading = true,
            error = null
        )

        profilJob = viewModelScope.launch {
            try {
                val response = api.getProfil(studentId)

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil profil siswa"
                    )
                }

                if (_uiState.value.selectedStudentId != studentId) {
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    profil = response.data,
                    loading = false,
                    error = null
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.selectedStudentId == studentId) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = e.userMessage("Gagal mengambil profil siswa")
                    )
                }
            }
        }
    }

    fun retry() {
        val state = _uiState.value

        if (state.siswa.isEmpty()) {
            loadSiswa()
        } else {
            val id = state.selectedStudentId
            if (id != null) {
                pilihSiswa(id)
            } else {
                loadSiswa()
            }
        }
    }
}
