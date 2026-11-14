package id.sch.santaagnes.sim.ui.admin.jenispelanggaran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.JenisPelanggaranApi
import id.sch.santaagnes.sim.data.remote.JenisPelanggaranDto
import id.sch.santaagnes.sim.data.remote.SimpanJenisPelanggaranRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JenisPelanggaranUiState(
    val daftar: List<JenisPelanggaranDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val pesan: String? = null
)

@HiltViewModel
class JenisPelanggaranViewModel @Inject constructor(
    private val api: JenisPelanggaranApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JenisPelanggaranUiState()
    )

    val uiState = _uiState.asStateFlow()

    fun getJenisPelanggaran() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val daftar = mutableListOf<JenisPelanggaranDto>()
                var page = 1
                var totalPages: Int

                do {
                    val response = api.getAll(page = page)

                    if (!response.success) {
                        throw Exception(
                            response.error?.message
                                ?: "Gagal mengambil data"
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
        body: SimpanJenisPelanggaranRequest,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.loading) return

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
                            ?: "Gagal menyimpan data"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    pesan = "Data berhasil disimpan"
                )

                onSuccess()
                getJenisPelanggaran()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Gagal menyimpan"
                )
            }
        }
    }

    fun hapus(id: Int) {
        if (_uiState.value.loading) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.hapus(id)

                if (!response.isSuccessful) {
                    val pesan = if (response.code() == 409) {
                        "Jenis pelanggaran sudah digunakan dalam riwayat siswa"
                    } else {
                        "Gagal menghapus (HTTP ${response.code()})"
                    }

                    throw Exception(pesan)
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    pesan = "Data berhasil dihapus"
                )

                getJenisPelanggaran()

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
            pesan = null,
            error = null
        )
    }
}