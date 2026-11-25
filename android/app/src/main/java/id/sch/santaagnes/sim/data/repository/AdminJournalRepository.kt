package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.AdminJournalDto
import id.sch.santaagnes.sim.data.remote.ReportApi
import javax.inject.Inject

/** Mengikuti amplop paginasi backend /api/v1/admin/teaching-journals. */
data class AdminJournalPage(val rows: List<AdminJournalDto>, val page: Int, val totalPages: Int, val total: Int)

class AdminJournalRepository @Inject constructor(private val api: ReportApi) {
    suspend fun getPage(
        page: Int,
        startDate: String?,
        endDate: String?,
        teacherId: Int? = null,
        classGroupId: Int? = null
    ): AdminJournalPage {
        val response = api.getAdminJournals(
            page = page,
            limit = 10,
            startDate = startDate,
            endDate = endDate,
            teacherId = teacherId,
            classGroupId = classGroupId
        )
        if (!response.success) error(response.error?.message ?: "Gagal mengambil jurnal mengajar.")
        val meta = response.meta ?: error("Informasi halaman jurnal tidak tersedia.")
        return AdminJournalPage(response.data.orEmpty(), meta.page, meta.totalPages, meta.total)
    }
}
