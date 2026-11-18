package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.ExportFiles
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.ReportApi
import id.sch.santaagnes.sim.data.remote.TeachingJournalReportDto
import id.sch.santaagnes.sim.domain.model.ReportPage
import javax.inject.Inject

/** LAP-06: laporan jurnal mengajar dengan filter dan ekspor yang identik. */
class TeachingJournalReportRepository @Inject constructor(
    private val api: ReportApi,
    private val errors: ApiErrorMapper,
    private val files: ExportFiles
) {
    suspend fun load(
        page: Int, classId: Int?, teacherId: Int?, from: String?, to: String?
    ): Resource<ReportPage<TeachingJournalReportDto>> = errors.call {
        val response = api.getTeachingJournalsReport(page, 20, classId, teacherId, from, to)
        check(response.success) { response.error?.message ?: "Laporan jurnal gagal dimuat." }
        ReportPage(
            items = response.data.orEmpty(),
            page = page,
            total = response.meta?.total ?: response.data.orEmpty().size,
            hasMore = page < (response.meta?.totalPages ?: page)
        )
    }

    suspend fun export(
        classId: Int?, teacherId: Int?, from: String?, to: String?, destination: String
    ): Resource<Boolean> = errors.call {
        files.write(api.exportTeachingJournalsReport(classId, teacherId, from, to), destination)
    }
}
