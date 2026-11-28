package id.sch.santaagnes.sim.ui.student.survei

import retrofit2.HttpException
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

data class IsiSurveiMinatState(
    val surveys: List<SurveiSiswaDto> = emptyList(),
    val detail: DetailSurveiSiswaDto? = null,
    val selectedIds: Set<Int> = emptySet(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class IsiSurveiMinatViewModel @Inject constructor(
    private val api: IsiSurveiMinatApi
) : ViewModel() {

    private val _state = MutableStateFlow(
        IsiSurveiMinatState()
    )

    val state = _state.asStateFlow()

    fun loadSurveys() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            try {
                val response = api.getSurveys()

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal memuat survei"
                    )
                }

                _state.value = _state.value.copy(
                    surveys = response.data.orEmpty(),
                    loading = false
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.userMessage("Terjadi kesalahan")
                )
            }
        }
    }

    fun pilihSurvei(id: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                detail = null,
                selectedIds = emptySet(),
                loading = true,
                error = null
            )

            try {
                val response = api.getDetail(id)

                if (!response.success || response.data == null) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal memuat detail survei"
                    )
                }

                _state.value = _state.value.copy(
                    detail = response.data,
                    selectedIds = response.data.survey
                        .selectedSubjectIds.toSet(),
                    loading = false
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.userMessage("Terjadi kesalahan")
                )
            }
        }
    }

    fun pilihMapel(id: Int, checked: Boolean) {
        val current = _state.value.selectedIds

        _state.value = _state.value.copy(
            selectedIds = if (checked) {
                current + id
            } else {
                current - id
            }
        )
    }

    fun simpan() {
        val current = _state.value
        val survey = current.detail?.survey ?: return

        if (current.saving || current.loading) return

        if (current.selectedIds.isEmpty()) {
            _state.value = current.copy(
                error = "Pilih minimal satu mata pelajaran."
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                saving = true,
                error = null
            )

            try {
                val response = api.submit(
                    survey.id,
                    JawabanSurveiRequest(
                        current.selectedIds.sorted()
                    )
                )

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal menyimpan jawaban"
                    )
                }

                _state.value = _state.value.copy(
                    saving = false,
                    detail = null,
                    message = "Jawaban survei berhasil disimpan."
                )

                loadSurveys()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    error = e.userMessage("Pengisian survei gagal")
                )
                if (e is HttpException && e.code() == 409) muatUlangStatus(survey.id)
            }
        }
    }

    // Survei ditutup saat halaman sudah terbuka: tampilan jadi hanya baca, pesan tetap terlihat.
    private suspend fun muatUlangStatus(id: Int) {
        try {
            val detail = api.getDetail(id).data
            val surveys = api.getSurveys().data
            _state.value = _state.value.copy(
                detail = detail ?: _state.value.detail,
                surveys = surveys ?: _state.value.surveys
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Pesan kegagalan simpan sudah tampil; status diperbarui saat daftar dimuat lagi.
        }
    }

    fun kembaliKeDaftar() {
        _state.value = _state.value.copy(
            detail = null,
            selectedIds = emptySet(),
            error = null
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(
            message = null
        )
    }

    fun retry() {
        val id = _state.value.detail?.survey?.id

        if (id != null) {
            pilihSurvei(id)
        } else {
            loadSurveys()
        }
    }
}
