package id.sch.santaagnes.sim.ui.student.paket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.DaftarPaketSiswaDto
import id.sch.santaagnes.sim.data.remote.PilihPaketApi
import id.sch.santaagnes.sim.data.remote.PilihPaketRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PilihPaketUiState(
    val data: DaftarPaketSiswaDto? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class PilihPaketViewModel @Inject constructor(
    private val api: PilihPaketApi
) : ViewModel() {

    private val _state = MutableStateFlow(
        PilihPaketUiState()
    )

    val state = _state.asStateFlow()

    fun loadPaket() {
        if (_state.value.loading || _state.value.saving) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.getDaftarPaket()

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil daftar paket."
                    )
                }

                _state.value = _state.value.copy(
                    data = response.data,
                    loading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Terjadi kesalahan."
                )
            }
        }
    }

    fun pilihPaket(packageId: Int) {
        val current = _state.value

        if (current.loading || current.saving) return

        val paket = current.data?.packages?.find {
            it.id == packageId
        } ?: return

        if (paket.isSelected || paket.remainingCapacity <= 0) {
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                saving = true,
                error = null
            )

            try {
                val response = api.pilihPaket(
                    PilihPaketRequest(packageId)
                )

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal memilih paket."
                    )
                }

                _state.value = _state.value.copy(
                    saving = false,
                    message = response.data.message
                )

                loadPaket()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    error = e.message ?: "Gagal menyimpan pilihan."
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(
            message = null
        )
    }
}
