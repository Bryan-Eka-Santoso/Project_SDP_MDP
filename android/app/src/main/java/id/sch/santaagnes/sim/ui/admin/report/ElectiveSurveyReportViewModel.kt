package id.sch.santaagnes.sim.ui.admin.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

data class ElectiveSurveyReportState(
    val items: List<ElectiveSurveyReportDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val totalPages: Int = 0,
    val surveyId: Int? = null,
    val academicYearId: Int? = null,
    val gradeLevel: String? = null,
    val status: String? = null,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class ElectiveSurveyReportViewModel @Inject constructor(
    private val api: ReportApi
) : ViewModel() {

    private val _state = MutableStateFlow(
        ElectiveSurveyReportState()
    )
    val state = _state.asStateFlow()

    private var loadJob: Job? = null

    fun setFilter(
        surveyId: Int?,
        academicYearId: Int?,
        gradeLevel: String?,
        status: String?
    ) {
        loadJob?.cancel()

        _state.value = _state.value.copy(
            surveyId = surveyId,
            academicYearId = academicYearId,
            gradeLevel = gradeLevel,
            status = status,
            items = emptyList(),
            page = 0,
            total = 0,
            totalPages = 0,
            loading = false,
            error = null
        )

        refresh()
    }

    fun refresh() {
        fetch(1)
    }

    fun loadMore() {
        val current = _state.value

        if (current.loading || current.page >= current.totalPages) {
            return
        }

        fetch(current.page + 1)
    }

    private fun fetch(page: Int) {
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            try {
                val filter = _state.value

                val response = api.getElectiveSurveys(
                    page = page,
                    limit = 20,
                    surveyId = filter.surveyId,
                    academicYearId = filter.academicYearId,
                    gradeLevel = filter.gradeLevel,
                    status = filter.status
                )

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal memuat laporan survei"
                    )
                }

                val data = response.data.orEmpty()

                val items = if (page == 1) {
                    data
                } else {
                    _state.value.items + data
                }

                _state.value = _state.value.copy(
                    items = items,
                    total = response.meta?.total ?: items.size,
                    page = page,
                    totalPages = response.meta?.totalPages ?: page,
                    loading = false
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message
                        ?: "Terjadi kesalahan"
                )
            }
        }
    }

    fun export(
        openOutput: suspend () -> OutputStream?
    ) {
        if (_state.value.exporting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                exporting = true,
                error = null
            )

            try {
                val filter = _state.value

                withContext(Dispatchers.IO) {
                    api.exportElectiveSurveys(
                        surveyId = filter.surveyId,
                        academicYearId = filter.academicYearId,
                        gradeLevel = filter.gradeLevel,
                        status = filter.status
                    ).use { body ->
                        val output = openOutput()
                            ?: throw Exception(
                                "Tidak dapat membuka file tujuan"
                            )

                        output.use { stream ->
                            body.byteStream().use { input ->
                                input.copyTo(stream)
                            }
                        }
                    }
                }

                _state.value = _state.value.copy(
                    exporting = false,
                    message = "Laporan Excel berhasil disimpan"
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    exporting = false,
                    error = e.message ?: "Gagal ekspor laporan"
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
