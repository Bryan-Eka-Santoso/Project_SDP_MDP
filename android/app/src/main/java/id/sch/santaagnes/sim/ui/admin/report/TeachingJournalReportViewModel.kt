package id.sch.santaagnes.sim.ui.admin.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.TeachingJournalReportDto
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.data.repository.TeachingJournalReportRepository
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.TeacherOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TeachingJournalReportState(
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val items: List<TeachingJournalReportDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val classOptions: List<NamedId> = emptyList(),
    val teacherOptions: List<TeacherOption> = emptyList(),
    val classGroupId: Int? = null,
    val teacherId: Int? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val error: String? = null
)

@HiltViewModel
class TeachingJournalReportViewModel @Inject constructor(
    private val journals: TeachingJournalReportRepository,
    private val reports: ReportRepository
) : ViewModel() {
    private var loadJob: Job? = null
    private val mutable = MutableStateFlow(TeachingJournalReportState())
    val state = mutable.asStateFlow()
    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    init { loadOptions(); refresh() }

    private fun loadOptions() = viewModelScope.launch {
        when (val result = reports.options()) {
            is Resource.Success -> mutable.value = mutable.value.copy(
                classOptions = result.data.classGroups,
                teacherOptions = result.data.teachers
            )
            is Resource.Error -> Unit // Laporan tetap dapat dilihat tanpa pilihan filter.
        }
    }

    fun selectClass(id: Int?) {
        if (mutable.value.classGroupId == id) return
        mutable.value = mutable.value.copy(classGroupId = id)
        refresh()
    }
    fun selectTeacher(id: Int?) {
        if (mutable.value.teacherId == id) return
        mutable.value = mutable.value.copy(teacherId = id)
        refresh()
    }
    fun selectDateFrom(date: LocalDate) {
        val old = mutable.value
        if (old.dateTo != null && date > old.dateTo) {
            mutableEvents.tryEmit("Tanggal awal tidak boleh melewati tanggal akhir.")
            return
        }
        if (old.dateFrom == date) return
        mutable.value = old.copy(dateFrom = date)
        refresh()
    }
    fun selectDateTo(date: LocalDate) {
        val old = mutable.value
        if (old.dateFrom != null && date < old.dateFrom) {
            mutableEvents.tryEmit("Tanggal akhir tidak boleh mendahului tanggal awal.")
            return
        }
        if (old.dateTo == date) return
        mutable.value = old.copy(dateTo = date)
        refresh()
    }
    fun clearDates() {
        if (mutable.value.dateFrom == null && mutable.value.dateTo == null) return
        mutable.value = mutable.value.copy(dateFrom = null, dateTo = null)
        refresh()
    }

    fun refresh() { loadJob?.cancel(); loadPage(false) }
    fun loadMore() = loadPage(true)

    private fun loadPage(more: Boolean) {
        val before = mutable.value
        if (more && (before.loading || !before.hasMore)) return
        loadJob = viewModelScope.launch {
            val page = if (more) before.page + 1 else 1
            val classId = mutable.value.classGroupId
            val teacherId = mutable.value.teacherId
            val from = mutable.value.dateFrom?.toString()
            val to = mutable.value.dateTo?.toString()
            mutable.value = mutable.value.copy(loading = true, error = null)
            when (val result = journals.load(page, classId, teacherId, from, to)) {
                is Resource.Success -> {
                    val current = mutable.value
                    if (current.classGroupId == classId && current.teacherId == teacherId &&
                        current.dateFrom?.toString() == from && current.dateTo?.toString() == to) {
                        mutable.value = current.copy(
                            loading = false,
                            items = if (more) before.items + result.data.items else result.data.items,
                            total = result.data.total,
                            page = page,
                            hasMore = result.data.hasMore
                        )
                    } else mutable.value = current.copy(loading = false)
                }
                is Resource.Error -> mutable.value = mutable.value.copy(loading = false, error = result.message)
            }
        }
    }

    fun exportTo(destination: String) {
        if (mutable.value.exporting) return
        viewModelScope.launch {
            val current = mutable.value
            mutable.value = current.copy(exporting = true)
            val result = journals.export(
                current.classGroupId, current.teacherId,
                current.dateFrom?.toString(), current.dateTo?.toString(), destination
            )
            mutable.value = mutable.value.copy(exporting = false)
            mutableEvents.emit(when (result) {
                is Resource.Success -> if (result.data) "Laporan jurnal berhasil disimpan." else "Berkas gagal disimpan."
                is Resource.Error -> result.message
            })
        }
    }
}
