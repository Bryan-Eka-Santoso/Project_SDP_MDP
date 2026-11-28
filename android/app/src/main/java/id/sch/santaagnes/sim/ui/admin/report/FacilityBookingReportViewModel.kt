package id.sch.santaagnes.sim.ui.admin.report

import id.sch.santaagnes.sim.core.userMessage
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

data class FacilityBookingReportState(
    val items: List<FacilityBookingReportDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val totalPages: Int = 0,
    val facilityId: Int? = null,
    val status: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class FacilityBookingReportViewModel @Inject constructor(
    private val api: ReportApi
) : ViewModel() {

    private val _state = MutableStateFlow(
        FacilityBookingReportState()
    )

    val state = _state.asStateFlow()

    private var loadJob: Job? = null

    fun setFilter(
        facilityId: Int?,
        status: String?,
        dateFrom: String?,
        dateTo: String?
    ) {
        loadJob?.cancel()

        _state.value = _state.value.copy(
            facilityId = facilityId,
            status = status,
            dateFrom = dateFrom,
            dateTo = dateTo,
            items = emptyList(),
            total = 0,
            page = 0,
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

        if (
            current.loading ||
            current.page >= current.totalPages
        ) return

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

                val response = api.getFacilityBookings(
                    page = page,
                    limit = 20,
                    facilityId = filter.facilityId,
                    status = filter.status,
                    dateFrom = filter.dateFrom,
                    dateTo = filter.dateTo
                )

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal mengambil laporan peminjaman"
                    )
                }

                val data = response.data.orEmpty()

                val merged = if (page == 1) {
                    data
                } else {
                    _state.value.items + data
                }

                _state.value = _state.value.copy(
                    items = merged,
                    page = page,
                    total = response.meta?.total ?: merged.size,
                    totalPages = response.meta?.totalPages ?: page,
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

                val response = withContext(Dispatchers.IO) {
                    api.exportFacilityBookings(
                        facilityId = filter.facilityId,
                        status = filter.status,
                        dateFrom = filter.dateFrom,
                        dateTo = filter.dateTo
                    )
                }

                withContext(Dispatchers.IO) {
                    response.use { body ->
                        val output = openOutput()
                            ?: throw Exception(
                                "Gagal membuka file tujuan"
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
                    error = e.userMessage("Gagal ekspor laporan")
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
