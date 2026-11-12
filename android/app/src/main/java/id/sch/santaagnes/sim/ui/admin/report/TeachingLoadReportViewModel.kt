package id.sch.santaagnes.sim.ui.admin.report

import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.domain.model.ReportOptions
import id.sch.santaagnes.sim.domain.model.ReportPage
import id.sch.santaagnes.sim.domain.model.TeachingLoadFilter
import id.sch.santaagnes.sim.domain.model.TeachingLoadRow
import javax.inject.Inject

/** Laporan beban mengajar guru (F-LAP-09). */
@HiltViewModel
class TeachingLoadReportViewModel @Inject constructor(
    private val repository: ReportRepository
) : ReportViewModel<TeachingLoadRow, TeachingLoadFilter>(TeachingLoadFilter()) {

    override suspend fun fetch(page: Int, filter: TeachingLoadFilter): Resource<ReportPage<TeachingLoadRow>> =
        repository.teachingLoad(page, filter)

    override suspend fun export(filter: TeachingLoadFilter, destination: String): Resource<Boolean> =
        repository.exportTeachingLoad(filter, destination)

    override suspend fun fetchOptions(): Resource<ReportOptions> = repository.options()

    override fun idOf(row: TeachingLoadRow): Int = row.teacherId

    fun selectTeacher(teacherId: Int?) = updateFilter { it.copy(teacherId = teacherId) }
}
