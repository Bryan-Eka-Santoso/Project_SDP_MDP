package id.sch.santaagnes.sim.ui.teacher.pelanggaran

import id.sch.santaagnes.sim.core.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InputPelanggaranUiState(
    val siswa: List<SiswaPelanggaranDto> = emptyList(),
    val jenis: List<JenisPelanggaranGuruDto> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val hasil: HasilPelanggaranDto? = null
)

@HiltViewModel
class InputPelanggaranViewModel @Inject constructor(
    private val api: PelanggaranApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        InputPelanggaranUiState()
    )

    val uiState = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val responseSiswa = api.getSiswa()
                val responseJenis = api.getJenis()

                if (!responseSiswa.success || !responseJenis.success) {
                    throw Exception(
                        responseSiswa.error?.message
                            ?: responseJenis.error?.message
                            ?: "Gagal mengambil data"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    siswa = responseSiswa.data.orEmpty(),
                    jenis = responseJenis.data.orEmpty(),
                    loading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.userMessage("Terjadi kesalahan")
                )
            }
        }
    }

    fun simpan(
        body: InputPelanggaranRequest,
        onSuccess: (HasilPelanggaranDto) -> Unit
    ) {
        if (_uiState.value.saving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saving = true,
                error = null
            )

            try {
                val response = api.simpan(body)

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal menyimpan pelanggaran"
                    )
                }

                val hasil = response.data

                _uiState.value = _uiState.value.copy(
                    saving = false,
                    hasil = hasil
                )

                onSuccess(hasil)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = e.userMessage("Gagal menyimpan")
                )
            }
        }
    }
}