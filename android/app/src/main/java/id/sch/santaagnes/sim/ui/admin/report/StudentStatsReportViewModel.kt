package id.sch.santaagnes.sim.ui.admin.report

import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.domain.model.ReportOptions
import id.sch.santaagnes.sim.domain.model.ReportPage
import id.sch.santaagnes.sim.domain.model.StudentStatsFilter
import id.sch.santaagnes.sim.domain.model.StudentStatsRow
import javax.inject.Inject

/** Laporan statistik kesiswaan per kelas pada semester aktif (F-LAP-10). */
@HiltViewModel
class StudentStatsReportViewModel @Inject constructor(
    private val repository: ReportRepository
) : ReportViewModel<StudentStatsRow, StudentStatsFilter>(StudentStatsFilter()) {

    override suspend fun fetch(page: Int, filter: StudentStatsFilter): Resource<ReportPage<StudentStatsRow>> =
        repository.studentStats(page, filter)

    override suspend fun export(filter: StudentStatsFilter, destination: String): Resource<Boolean> =
        repository.exportStudentStats(filter, destination)

    override suspend fun fetchOptions(): Resource<ReportOptions> = repository.options()

    override fun idOf(row: StudentStatsRow): Int = row.classGroupId

    fun selectClassGroup(classGroupId: Int?) = updateFilter { it.copy(classGroupId = classGroupId) }

    fun selectGradeLevel(gradeLevel: String?) = updateFilter { it.copy(gradeLevel = gradeLevel) }
}
