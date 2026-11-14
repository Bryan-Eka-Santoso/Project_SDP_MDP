package id.sch.santaagnes.sim.ui.admin.matapelajaran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.MataPelajaranApi
import id.sch.santaagnes.sim.data.remote.MataPelajaranDto
import id.sch.santaagnes.sim.data.remote.SimpanMataPelajaranRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MataPelajaranUiState(
    val daftar: List<MataPelajaranDto> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val pesan: String? = null
)

@HiltViewModel
class MataPelajaranViewModel @Inject constructor(
    private val api: MataPelajaranApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(MataPelajaranUiState())
    val uiState = _uiState.asStateFlow()

    fun getMataPelajaran() {
        if (_uiState.value.saving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                error = null
            )

            try {
                val daftar = mutableListOf<MataPelajaranDto>()
                var page = 1
                var totalPages: Int

                do {
                    val response = api.getAll(page = page)

                    if (!response.success || response.data == null) {
                        throw Exception(
                            response.error?.message ?: "Gagal mengambil data"
                        )
                    }

                    daftar.addAll(response.data)
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
        request: SimpanMataPelajaranRequest,
        onSuccess: () -> Unit
    ) {
        if (_uiState.value.saving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saving = true,
                error = null
            )

            try {
                val response = if (id == null) {
                    api.tambah(request)
                } else {
                    api.edit(id, request)
                }

                if (!response.success) {
                    throw Exception(
                        response.error?.message ?: "Gagal menyimpan data"
                    )
                }

                _uiState.value = _uiState.value.copy(
                    saving = false,
                    pesan = "Mata pelajaran berhasil disimpan"
                )

                onSuccess()
                getMataPelajaran()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    error = e.message ?: "Gagal menyimpan"
                )
            }
        }
    }

    fun hapus(id: Int) {
        if (_uiState.value.saving) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                saving = true,
                error = null
            )

            try {
                val response = api.hapus(id)

                if (!response.isSuccessful) {
                    val pesan = if (response.code() == 409) {
                        "Mata pelajaran masih digunakan dalam data akademik"
                    } else {
                        "Gagal menghapus (HTTP ${response.code()})"
                    }
                    throw Exception(pesan)
                }

                _uiState.value = _uiState.value.copy(
                    saving = false,
                    pesan = "Mata pelajaran berhasil dihapus"
                )

                getMataPelajaran()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
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