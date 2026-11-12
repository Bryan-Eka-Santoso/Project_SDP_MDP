package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.core.UploadFiles
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.BillApi
import id.sch.santaagnes.sim.domain.model.Bill
import id.sch.santaagnes.sim.domain.model.BillFilter
import id.sch.santaagnes.sim.domain.model.BillImportResult
import id.sch.santaagnes.sim.domain.model.BillScope
import id.sch.santaagnes.sim.domain.model.BillScopeOption
import id.sch.santaagnes.sim.domain.model.BillViewer
import id.sch.santaagnes.sim.domain.model.PageResult
import javax.inject.Inject
import javax.inject.Singleton

/** Tagihan dari rekap yayasan (F-BYR-01/02). Hanya online; tidak ada pembayaran di aplikasi. */
interface BillRepository {

    /** Siapa yang melihat dan tagihan siapa saja yang bisa dibukanya (dari sesi tersimpan). */
    suspend fun viewer(): BillViewer

    /** Ingat anak yang dipilih wali murid agar layar lain membukanya lebih dulu. */
    suspend fun remember(scope: BillScope)

    /** [query] (NIS atau nama) hanya berlaku untuk daftar lintas siswa. */
    suspend fun bills(scope: BillScope, page: Int, filter: BillFilter, query: String?): Resource<PageResult<Bill>>

    suspend fun fileName(uri: String): String?

    suspend fun importBills(uri: String): Resource<BillImportResult>
}

@Singleton
class BillRepositoryImpl @Inject constructor(
    private val api: BillApi,
    private val session: SessionManager,
    private val errors: ApiErrorMapper,
    private val uploads: UploadFiles
) : BillRepository {

    override suspend fun viewer(): BillViewer {
        val role = session.getRole().orEmpty()
        val options = when (role) {
            "STUDENT" -> listOfNotNull(session.getStudentId()?.let { BillScopeOption(BillScope.Student(it), null) })
            "GUARDIAN" -> session.getChildren().map { BillScopeOption(BillScope.Student(it.id), it.name) }
            "TEACHER" -> session.getHomeroomClassGroups().map { BillScopeOption(BillScope.HomeroomClass(it.id), it.name) }
            "ADMIN" -> listOf(BillScopeOption(BillScope.All, null))
            else -> emptyList()
        }
        val active = session.getActiveStudentId()
        val initial = options.indexOfFirst { (it.scope as? BillScope.Student)?.studentId == active }
        return BillViewer(role, options, initialIndex = maxOf(initial, 0))
    }

    override suspend fun remember(scope: BillScope) {
        if (scope is BillScope.Student) session.saveActiveStudentId(scope.studentId)
    }

    override suspend fun bills(
        scope: BillScope,
        page: Int,
        filter: BillFilter,
        query: String?
    ): Resource<PageResult<Bill>> = errors.call {
        val status = if (filter == BillFilter.PAID) "PAID" else null
        val overdue = if (filter == BillFilter.OVERDUE) true else null
        val response = when (scope) {
            is BillScope.Student -> api.getStudentBills(scope.studentId, page, PAGE_SIZE, status, overdue)
            is BillScope.HomeroomClass ->
                api.getBills(page, PAGE_SIZE, status, overdue, scope.classGroupId, query?.trim()?.ifEmpty { null })
            BillScope.All -> api.getBills(page, PAGE_SIZE, status, overdue, null, query?.trim()?.ifEmpty { null })
        }
        check(response.success) { "Tagihan gagal dimuat." }
        PageResult(
            items = response.data.orEmpty().map { it.toDomain() },
            page = page,
            hasMore = page < (response.meta?.totalPages ?: page)
        )
    }

    override suspend fun fileName(uri: String): String? = uploads.name(uri)

    override suspend fun importBills(uri: String): Resource<BillImportResult> = errors.call {
        val part = uploads.xlsxPart(uri, "pembayaran.xlsx")
        checkNotNull(api.importBills(part).data) { "Impor gagal." }.toDomain()
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
