package id.sch.santaagnes.sim.ui.admin.fasilitas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.FasilitasApi
import id.sch.santaagnes.sim.data.remote.FasilitasDto
import id.sch.santaagnes.sim.data.remote.SimpanFasilitasRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FasilitasUiState(
    val daftar: List<FasilitasDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val pesan: String? = null
)

@HiltViewModel
class FasilitasViewModel @Inject constructor(
    private val api: FasilitasApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(FasilitasUiState())
    val uiState = _uiState.asStateFlow()

    private var filterType: String? = null

    fun getFasilitas(type: String? = filterType) {
        filterType = type

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val daftar = mutableListOf<FasilitasDto>()
                var page = 1
                var totalPages: Int

                do {
                    val response = api.getAll(
                        page = page,
                        type = type
                    )

                    if (!response.success) {
                        throw Exception(
                            response.error?.message
                                ?: "Gagal mengambil fasilitas"
                        )
                    }

                    daftar.addAll(response.data.orEmpty())

                    totalPages = response.meta?.totalPages ?: 1
                    page++
                } while (page <= totalPages)

                _uiState.value = _uiState.value.copy(
                    daftar = daftar,
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

    fun simpan(
        id: Int?,
        body: SimpanFasilitasRequest,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = if (id == null) {
                    api.tambah(body)
                } else {
                    api.edit(id, body)
                }

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal menyimpan fasilitas"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    pesan = "Fasilitas berhasil disimpan"
                )

                onSuccess()
                getFasilitas()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Gagal menyimpan"
                )
            }
        }
    }

    fun hapus(id: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.hapus(id)

                if (!response.isSuccessful) {
                    throw Exception(
                        if (response.code() == 409) {
                            "Fasilitas masih digunakan"
                        } else {
                            "Gagal menghapus (HTTP ${response.code()})"
                        }
                    )
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    pesan = "Fasilitas berhasil dihapus"
                )

                getFasilitas()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Gagal menghapus"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            error = null,
            pesan = null
        )
    }
}