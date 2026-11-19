package id.sch.santaagnes.sim.ui.admin.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

data class ViolationReportState(
    val items: List<ViolationReportDto> = emptyList(),
    val types: List<JenisPelanggaranDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val totalPages: Int = 0,
    val classGroupId: Int? = null,
    val violationTypeId: Int? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class ViolationReportViewModel @Inject constructor(
    private val api: ReportApi,
    private val jenisApi: JenisPelanggaranApi
) : ViewModel() {

    private val _state = MutableStateFlow(ViolationReportState())
    val state = _state.asStateFlow()

    private val limit = 20

    fun loadTypes() {
        viewModelScope.launch {
            try {
                val response = jenisApi.getAll(
                    page = 1,
                    limit = 100
                )

                if (response.success) {
                    _state.value = _state.value.copy(
                        types = response.data.orEmpty()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Filter opsional, daftar laporan tetap bisa dibuka.
            }
        }
    }

    fun setFilter(
        classGroupId: Int?,
        violationTypeId: Int?,
        dateFrom: String?,
        dateTo: String?
    ) {
        _state.value = _state.value.copy(
            classGroupId = classGroupId,
            violationTypeId = violationTypeId,
            dateFrom = dateFrom,
            dateTo = dateTo,
            items = emptyList(),
            page = 0,
            total = 0,
            totalPages = 0
        )

        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
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
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null
            )

            try {
                val filter = _state.value

                val response = api.getViolations(
                    page = page,
                    limit = limit,
                    classGroupId = filter.classGroupId,
                    violationTypeId = filter.violationTypeId,
                    dateFrom = filter.dateFrom,
                    dateTo = filter.dateTo
                )

                if (!response.success) {
                    throw Exception(
                        response.error?.message
                            ?: "Gagal memuat laporan pelanggaran"
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
                    page = page,
                    total = response.meta?.total ?: items.size,
                    totalPages = response.meta?.totalPages ?: page,
                    loading = false
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Terjadi kesalahan"
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
                    val output = openOutput()
                        ?: throw Exception(
                            "File tujuan tidak dapat dibuka"
                        )

                    output.use { stream ->
                        api.exportViolations(
                            classGroupId = filter.classGroupId,
                            violationTypeId = filter.violationTypeId,
                            dateFrom = filter.dateFrom,
                            dateTo = filter.dateTo
                        ).use { response ->
                            response.byteStream().use { input ->
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
        _state.value = _state.value.copy(message = null)
    }
}
