package id.sch.santaagnes.sim.ui.peminjaman

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AjukanPeminjamanUiState(
    val fasilitas: List<FasilitasPeminjamanDto> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val berhasil: HasilPengajuanDto? = null
)

@HiltViewModel
class AjukanPeminjamanViewModel @Inject constructor(
    private val api: AjukanPeminjamanApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AjukanPeminjamanUiState()
    )

    val uiState = _uiState.asStateFlow()

    fun loadFasilitas() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.getFasilitas()

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal memuat fasilitas"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    fasilitas = response.data.orEmpty(),
                    loading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Terjadi kesalahan"
                )
            }
        }
    }

    fun ajukan(request: AjukanPeminjamanRequest) {
        if (_uiState.value.saving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saving = true,
                error = null,
                berhasil = null
            )

            try {
                val response = api.ajukan(request)

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengajukan peminjaman"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    saving = false,
                    berhasil = response.data
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = e.message
                        ?: "Pengajuan gagal disimpan"
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
