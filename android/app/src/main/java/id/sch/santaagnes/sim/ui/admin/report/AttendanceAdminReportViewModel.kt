package id.sch.santaagnes.sim.ui.admin.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.AttendanceAdminReportDto
import id.sch.santaagnes.sim.data.repository.AttendanceAdminReportRepository
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.domain.model.NamedId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AttendanceAdminReportState(
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val items: List<AttendanceAdminReportDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val classOptions: List<NamedId> = emptyList(),
    val classGroupId: Int? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val error: String? = null
)

@HiltViewModel
class AttendanceAdminReportViewModel @Inject constructor(
    private val attendance: AttendanceAdminReportRepository,
    private val reports: ReportRepository
) : ViewModel() {
    private var loadJob: Job? = null
    private val mutable = MutableStateFlow(AttendanceAdminReportState())
    val state = mutable.asStateFlow()
    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    init { loadOptions(); refresh() }

    private fun loadOptions() = viewModelScope.launch {
        when (val result = reports.options()) {
            is Resource.Success -> mutable.value = mutable.value.copy(classOptions = result.data.classGroups)
            is Resource.Error -> Unit // Laporan tetap dapat dimuat tanpa filter kelas.
        }
    }

    fun selectClass(classId: Int?) {
        if (mutable.value.classGroupId == classId) return
        mutable.value = mutable.value.copy(classGroupId = classId)
        refresh()
    }

    // Tanggal menggunakan LocalDate sehingga format query backend selalu YYYY-MM-DD.
    // Periode kosong = seluruh semester aktif; tidak memakai tanggal dummy.
    fun selectDateFrom(date: LocalDate) {
        val current = mutable.value
        if (current.dateTo != null && date > current.dateTo) {
            mutableEvents.tryEmit("Tanggal awal tidak boleh melewati tanggal akhir.")
            return
        }
        if (current.dateFrom == date) return
        mutable.value = current.copy(dateFrom = date)
        refresh()
    }

    fun selectDateTo(date: LocalDate) {
        val current = mutable.value
        if (current.dateFrom != null && date < current.dateFrom) {
            mutableEvents.tryEmit("Tanggal akhir tidak boleh mendahului tanggal awal.")
            return
        }
        if (current.dateTo == date) return
        mutable.value = current.copy(dateTo = date)
        refresh()
    }

    fun clearDates() {
        if (mutable.value.dateFrom == null && mutable.value.dateTo == null) return
        mutable.value = mutable.value.copy(dateFrom = null, dateTo = null)
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadPage(false)
    }
    fun loadMore() = loadPage(true)

    private fun loadPage(more: Boolean) {
        val current = mutable.value
        if (more && (current.loading || !current.hasMore)) return
        loadJob = viewModelScope.launch {
            val page = if (more) current.page + 1 else 1
            val selected = mutable.value.classGroupId
            val from = mutable.value.dateFrom?.toString()
            val to = mutable.value.dateTo?.toString()
            mutable.value = mutable.value.copy(loading = true, error = null)
            when (val result = attendance.load(page, selected, from, to)) {
                is Resource.Success -> {
                    // Abaikan respons lama jika pengguna sudah mengganti filter.
                    if (mutable.value.classGroupId == selected && mutable.value.dateFrom?.toString() == from && mutable.value.dateTo?.toString() == to) {
                        mutable.value = mutable.value.copy(
                            loading = false,
                            items = if (more) (current.items + result.data.items) else result.data.items,
                            total = result.data.total,
                            page = page,
                            hasMore = result.data.hasMore
                        )
                    } else mutable.value = mutable.value.copy(loading = false)
                }
                is Resource.Error -> mutable.value = mutable.value.copy(loading = false, error = result.message)
            }
        }
    }

    fun exportTo(destination: String) {
        if (mutable.value.exporting) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(exporting = true)
            val selected = mutable.value.classGroupId
            val from = mutable.value.dateFrom?.toString()
            val to = mutable.value.dateTo?.toString()
            val result = attendance.export(selected, from, to, destination)
            mutable.value = mutable.value.copy(exporting = false)
            mutableEvents.emit(when (result) {
                is Resource.Success -> if (result.data) "Laporan kehadiran berhasil disimpan." else "Berkas gagal disimpan."
                is Resource.Error -> result.message
            })
        }
    }
}
