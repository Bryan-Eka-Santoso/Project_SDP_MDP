
package id.sch.santaagnes.sim.ui.peminjaman

import id.sch.santaagnes.sim.core.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.data.remote.RiwayatPeminjamanApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RiwayatPeminjamanUiState(
    val daftar: List<PeminjamanDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val totalPages: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RiwayatPeminjamanViewModel @Inject constructor(
    private val api: RiwayatPeminjamanApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RiwayatPeminjamanUiState()
    )

    val uiState = _uiState.asStateFlow()

    fun refresh() {
        if (_uiState.value.loading) return
        ambilData(1)
    }

    fun muatLagi() {
        val state = _uiState.value

        if (state.loading || state.page >= state.totalPages) {
            return
        }

        ambilData(state.page + 1)
    }

    private fun ambilData(page: Int) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.getRiwayat(
                    page = page,
                    limit = 20
                )

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil riwayat"
                    )
                }

                val hasil = response.data.orEmpty()

                val daftarBaru = if (page == 1) {
                    hasil
                } else {
                    _uiState.value.daftar + hasil
                }

                _uiState.value = _uiState.value.copy(
                    daftar = daftarBaru,
                    total = response.meta?.total
                        ?: daftarBaru.size,
                    page = page,
                    totalPages = response.meta?.totalPages
                        ?: page,
                    loading = false,
                    error = null
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

    fun retry() {
        val state = _uiState.value

        if (state.page == 0) {
            refresh()
        } else if (state.page < state.totalPages) {
            muatLagi()
        } else {
            refresh()
        }
    }
}
