package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.AdminOverviewApi
import id.sch.santaagnes.sim.domain.model.AdminOverview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Jumlah seluruh data untuk dashboard admin (F-MST). Hanya online. */
interface AdminOverviewRepository {
    suspend fun overview(): Resource<AdminOverview>
}

@Singleton
class AdminOverviewRepositoryImpl @Inject constructor(
    private val api: AdminOverviewApi,
    private val errors: ApiErrorMapper
) : AdminOverviewRepository {

    override suspend fun overview(): Resource<AdminOverview> = errors.call {
        val counts = coroutineScope {
            PATHS.map { async { runCatching { total(it) } } }.map { it.await() }
        }
        // Semua gagal (mis. tidak ada jaringan): tampilkan pesan galatnya; sebagian gagal: kartunya kosong.
        if (counts.all { it.isFailure }) throw checkNotNull(counts.first().exceptionOrNull())
        fun at(index: Int): Int? = counts[index].getOrNull()
        AdminOverview(
            students = at(0),
            teachers = at(1),
            classes = at(2),
            users = at(3),
            subjects = at(4),
            scheduleSlots = at(5),
            scheduleChanges = at(6),
            facilities = at(7)
        )
    }

    private suspend fun total(path: String): Int {
        val response = api.list(path)
        check(response.success) { "Data gagal dimuat." }
        return response.meta?.total ?: response.data.orEmpty().size
    }

    private companion object {
        // urutan harus sama dengan field AdminOverview di atas
        val PATHS = listOf(
            "students", "teachers", "classes", "users", "subjects",
            "schedule-slots", "schedule-changes", "facilities"
        )
    }
}
