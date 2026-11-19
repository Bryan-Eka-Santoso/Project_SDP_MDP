package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.ExportFiles
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.AttendanceAdminReportDto
import id.sch.santaagnes.sim.data.remote.ReportApi
import id.sch.santaagnes.sim.domain.model.ReportPage
import javax.inject.Inject

/** LAP-02: laporan kehadiran Admin, tetap memakai Retrofit dan export handler project. */
class AttendanceAdminReportRepository @Inject constructor(
    private val api: ReportApi,
    private val errors: ApiErrorMapper,
    private val exportFiles: ExportFiles
) {
    suspend fun load(page: Int, classGroupId: Int?, dateFrom: String?, dateTo: String?): Resource<ReportPage<AttendanceAdminReportDto>> = errors.call {
        val response = api.getAttendanceReport(page, 20, classGroupId, dateFrom, dateTo)
        check(response.success) { response.error?.message ?: "Laporan kehadiran gagal dimuat." }
        ReportPage(
            items = response.data.orEmpty(),
            page = page,
            total = response.meta?.total ?: response.data.orEmpty().size,
            hasMore = page < (response.meta?.totalPages ?: page)
        )
    }

    suspend fun export(classGroupId: Int?, dateFrom: String?, dateTo: String?, destination: String): Resource<Boolean> = errors.call {
        exportFiles.write(api.exportAttendanceReport(classGroupId, dateFrom, dateTo), destination)
    }
}
