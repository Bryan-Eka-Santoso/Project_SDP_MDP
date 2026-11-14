package id.sch.santaagnes.sim.ui.admin.peminjaman

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.PeminjamanApi
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class PeminjamanUiState(
    val daftar: List<PeminjamanDto> = emptyList(),
    val loading: Boolean = false,
    val processing: Boolean = false,
    val error: String? = null,
    val pesan: String? = null,
    val filter: String? = "PENDING"
)

@HiltViewModel
class PeminjamanViewModel @Inject constructor(
    private val api: PeminjamanApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PeminjamanUiState()
    )

    val uiState = _uiState.asStateFlow()

    fun getPeminjaman(
        status: String? = _uiState.value.filter
    ) {
        if (_uiState.value.processing) return

        _uiState.value = _uiState.value.copy(
            filter = status
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val daftar = mutableListOf<PeminjamanDto>()
                var page = 1
                var totalPages: Int

                do {
                    val response = api.getAll(
                        page = page,
                        status = status
                    )

                    if (!response.success) {
                        throw Exception(
                            response.error?.message
                                ?: "Gagal mengambil peminjaman"
                        )
                    }

                    daftar.addAll(response.data.orEmpty())
                    totalPages = response.meta?.totalPages ?: 1
                    page++

                } while (page <= totalPages)

                // Abaikan hasil dari filter sebelumnya.
                if (_uiState.value.filter != status) {
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    daftar = daftar,
                    loading = false
                )

            } catch (e: Exception) {
                if (_uiState.value.filter != status) {
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Terjadi kesalahan"
                )
            }
        }
    }

    fun putuskan(id: Int, setujui: Boolean) {
        if (_uiState.value.processing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                processing = true,
                error = null
            )

            try {
                val response = if (setujui) {
                    api.setujui(id)
                } else {
                    api.tolak(id)
                }

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal memproses pengajuan"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    processing = false,
                    pesan = if (setujui) {
                        "Pengajuan berhasil disetujui"
                    } else {
                        "Pengajuan berhasil ditolak"
                    }
                )

                getPeminjaman()

            } catch (e: HttpException) {
                val message = when (e.code()) {
                    409 -> "Pengajuan sudah diproses atau terjadi bentrok jadwal."
                    403 -> "Hanya Admin yang dapat memproses pengajuan."
                    404 -> "Pengajuan tidak ditemukan."
                    else -> "Gagal memproses (HTTP ${e.code()})"
                }

                _uiState.value = _uiState.value.copy(
                    processing = false,
                    error = message
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    processing = false,
                    error = e.message ?: "Gagal memproses"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            pesan = null,
            error = null
        )
    }
}