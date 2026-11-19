package id.sch.santaagnes.sim.ui.admin.survei

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SurveiMinatState(
    val surveys: List<SurveiMinatDto> = emptyList(),
    val lookups: SurveiLookupsDto? = null,
    val recap: RekapSurveiDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class SurveiMinatViewModel @Inject constructor(
    private val api: SurveiMinatApi
) : ViewModel() {

    private val _state = MutableStateFlow(SurveiMinatState())
    val state = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        execute {
            val responseLookups = api.lookups()
            if (!responseLookups.success) {
                error(
                    responseLookups.error?.message
                        ?: "Data tahun ajaran gagal dimuat"
                )
            }

            val responseSurveys = api.list()
            if (!responseSurveys.success) {
                error(
                    responseSurveys.error?.message
                        ?: "Daftar survei gagal dimuat"
                )
            }

            _state.value = _state.value.copy(
                lookups = responseLookups.data,
                surveys = responseSurveys.data.orEmpty()
            )
        }
    }

    fun create(body: BuatSurveiRequest) = viewModelScope.launch {
        execute {
            val response = api.create(body)

            check(response.success) {
                response.error?.message ?: "Gagal membuat survei"
            }

            val result = api.list()

            _state.value = _state.value.copy(
                surveys = result.data.orEmpty(),
                message = "Draft survei berhasil dibuat"
            )
        }
    }

    fun publish(id: Int) {
        change(id, true)
    }

    fun close(id: Int) {
        change(id, false)
    }

    private fun change(
        id: Int,
        publishing: Boolean
    ) = viewModelScope.launch {
        execute {
            val response = if (publishing) {
                api.publish(id)
            } else {
                api.close(id)
            }

            check(response.success) {
                response.error?.message
                    ?: "Gagal mengubah status"
            }

            val result = api.list()

            _state.value = _state.value.copy(
                surveys = result.data.orEmpty(),
                message = if (publishing) {
                    "Survei berhasil diterbitkan"
                } else {
                    "Survei berhasil ditutup"
                }
            )
        }
    }

    fun recap(id: Int) = viewModelScope.launch {
        execute {
            val response = api.recap(id)

            if (!response.success || response.data == null) {
                error(
                    response.error?.message
                        ?: "Rekap gagal dimuat"
                )
            }

            _state.value = _state.value.copy(
                recap = response.data
            )
        }
    }

    fun clearRecap() {
        _state.value = _state.value.copy(
            recap = null
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(
            message = null
        )
    }

    private suspend fun execute(
        block: suspend () -> Unit
    ) {
        _state.value = _state.value.copy(
            loading = true,
            error = null
        )

        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = e.message ?: "Terjadi kesalahan"
            )
        } finally {
            _state.value = _state.value.copy(
                loading = false
            )
        }
    }
}
