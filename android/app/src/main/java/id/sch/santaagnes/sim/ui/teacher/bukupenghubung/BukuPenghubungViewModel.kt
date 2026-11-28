
package id.sch.santaagnes.sim.ui.teacher.bukupenghubung

import id.sch.santaagnes.sim.core.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BukuPenghubungUiState(
    val kelas: List<KelasPerwalianDto> = emptyList(),
    val siswa: List<SiswaBukuPenghubungDto> = emptyList(),
    val selectedClassId: Int? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val berhasil: CatatanBukuPenghubungDto? = null
)

@HiltViewModel
class BukuPenghubungViewModel @Inject constructor(
    private val api: BukuPenghubungApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BukuPenghubungUiState()
    )

    val uiState = _uiState.asStateFlow()

    fun getKelas() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.getKelas()

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil kelas"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    kelas = response.data.orEmpty(),
                    siswa = emptyList(),
                    selectedClassId = null,
                    loading = false
                )

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

    fun getSiswa(classId: Int) {

        _uiState.value = _uiState.value.copy(
            selectedClassId = classId,
            siswa = emptyList(),
            loading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val response = api.getSiswa(classId)

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil siswa"
                    )
                }

                // Abaikan hasil lama jika pilihan kelas berubah.
                if (_uiState.value.selectedClassId != classId) {
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    siswa = response.data.orEmpty(),
                    loading = false
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                if (_uiState.value.selectedClassId == classId) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = e.userMessage("Gagal mengambil siswa")
                    )
                }
            }
        }
    }

    fun simpan(request: CatatanBukuPenghubungRequest) {

        if (_uiState.value.loading || _uiState.value.saving) {
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saving = true,
                error = null,
                berhasil = null
            )

            try {
                val response = api.simpan(request)

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal menyimpan catatan"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    saving = false,
                    berhasil = response.data
                )

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = e.userMessage("Gagal menyimpan catatan")
                )
            }
        }
    }

    fun clearBerhasil() {
        _uiState.value = _uiState.value.copy(
            berhasil = null
        )
    }
}
