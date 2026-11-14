package id.sch.santaagnes.sim.ui.admin.report

import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ReportOptions
import id.sch.santaagnes.sim.domain.model.ReportPage
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportFilter
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportRow
import java.time.LocalDate
import javax.inject.Inject

/** Laporan perubahan jadwal (F-LAP-07). */
@HiltViewModel
class ScheduleChangeReportViewModel @Inject constructor(
    private val repository: ReportRepository
) : ReportViewModel<ScheduleChangeReportRow, ScheduleChangeReportFilter>(ScheduleChangeReportFilter()) {

    override suspend fun fetch(
        page: Int,
        filter: ScheduleChangeReportFilter
    ): Resource<ReportPage<ScheduleChangeReportRow>> = repository.scheduleChanges(page, filter)

    override suspend fun export(filter: ScheduleChangeReportFilter, destination: String): Resource<Boolean> =
        repository.exportScheduleChanges(filter, destination)

    override suspend fun fetchOptions(): Resource<ReportOptions> = repository.options()

    override fun idOf(row: ScheduleChangeReportRow): Int = row.id

    fun selectClassGroup(classGroupId: Int?) = updateFilter { it.copy(classGroupId = classGroupId) }

    fun selectTeacher(teacherId: Int?) = updateFilter { it.copy(teacherId = teacherId) }

    fun selectType(type: ChangeType?) = updateFilter { it.copy(type = type) }

    // Rentang tanggal dijaga tetap valid (awal tidak setelah akhir) supaya server tidak menolaknya.
    fun selectDateFrom(date: LocalDate?) = updateFilter {
        val to = it.dateTo
        it.copy(dateFrom = date, dateTo = if (date != null && to != null && date > to) date else to)
    }

    fun selectDateTo(date: LocalDate?) = updateFilter {
        val from = it.dateFrom
        it.copy(dateTo = date, dateFrom = if (date != null && from != null && date < from) date else from)
    }

    fun clearDates() = updateFilter { it.copy(dateFrom = null, dateTo = null) }
}
