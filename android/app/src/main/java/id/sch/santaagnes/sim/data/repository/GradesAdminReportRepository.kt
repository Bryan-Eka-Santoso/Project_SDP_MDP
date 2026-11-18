package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.ExportFiles
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.GradesAdminReportDto
import id.sch.santaagnes.sim.data.remote.ReportApi
import id.sch.santaagnes.sim.domain.model.ReportPage
import javax.inject.Inject

/** LAP-01: laporan nilai Admin, tetap memakai Retrofit dan export handler project. */
class GradesAdminReportRepository @Inject constructor(
    private val api: ReportApi,
    private val errors: ApiErrorMapper,
    private val exportFiles: ExportFiles
) {
    suspend fun load(page: Int, classGroupId: Int?): Resource<ReportPage<GradesAdminReportDto>> = errors.call {
        val response = api.getGradesReport(page, 20, classGroupId)
        check(response.success) { response.error?.message ?: "Laporan nilai gagal dimuat." }
        ReportPage(
            items = response.data.orEmpty(),
            page = page,
            total = response.meta?.total ?: response.data.orEmpty().size,
            hasMore = page < (response.meta?.totalPages ?: page)
        )
    }

    suspend fun export(classGroupId: Int?, destination: String): Resource<Boolean> = errors.call {
        exportFiles.write(api.exportGradesReport(classGroupId), destination)
    }
}
