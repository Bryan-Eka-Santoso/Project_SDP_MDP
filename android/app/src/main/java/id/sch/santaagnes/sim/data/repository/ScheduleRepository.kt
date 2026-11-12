package id.sch.santaagnes.sim.data.repository

import androidx.room.withTransaction
import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.AppDatabase
import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.ScheduleApi
import id.sch.santaagnes.sim.domain.model.ScheduleEntry
import id.sch.santaagnes.sim.domain.model.ScheduleScope
import id.sch.santaagnes.sim.domain.model.ScheduleViewer
import id.sch.santaagnes.sim.domain.model.ScopeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface ScheduleRepository {

    /** Role pengguna dan pilihan jadwal yang boleh ia lihat (dari data sesi tersimpan). */
    suspend fun viewer(): ScheduleViewer

    /** Ingat anak yang dipilih wali murid agar layar berikutnya membukanya lebih dulu. */
    suspend fun remember(scope: ScheduleScope)

    /** Jadwal tersimpan (cache) pada rentang tanggal; dipancarkan ulang setiap cache berubah. */
    fun observe(scope: ScheduleScope, from: LocalDate, to: LocalDate): Flow<List<ScheduleEntry>>

    /** Ambil dari server lalu ganti isi cache pada rentang itu. Cache lama tetap bila gagal. */
    suspend fun refresh(scope: ScheduleScope, from: LocalDate, to: LocalDate): Resource<Unit>
}

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val api: ScheduleApi,
    private val dao: LocalDao,
    private val database: AppDatabase,
    private val session: SessionManager,
    private val errors: ApiErrorMapper
) : ScheduleRepository {

    override suspend fun viewer(): ScheduleViewer {
        val role = session.getRole().orEmpty()
        val options = when (role) {
            "STUDENT" -> listOf(ScopeOption(ScheduleScope.Own, null))
            "TEACHER" -> listOf(ScopeOption(ScheduleScope.Own, null)) +
                session.getHomeroomClassGroups()
                    .map { ScopeOption(ScheduleScope.HomeroomClass(it.id), it.name) }
            "GUARDIAN" -> session.getChildren()
                .map { ScopeOption(ScheduleScope.Child(it.id), it.name) }
            else -> emptyList()
        }
        val active = session.getActiveStudentId()
        val initial = options.indexOfFirst { (it.scope as? ScheduleScope.Child)?.studentId == active }
        return ScheduleViewer(role, options, initialIndex = maxOf(initial, 0))
    }

    override suspend fun remember(scope: ScheduleScope) {
        if (scope is ScheduleScope.Child) session.saveActiveStudentId(scope.studentId)
    }

    override fun observe(
        scope: ScheduleScope,
        from: LocalDate,
        to: LocalDate
    ): Flow<List<ScheduleEntry>> = flow {
        val accountId = session.getUserId()
        if (accountId == null) {
            emit(emptyList())
            return@flow
        }
        val source = dao.observeSchedules(accountId, cacheScope(scope), from.toString(), to.toString())
        emitAll(source.map { rows -> rows.map { it.toDomain() } })
    }

    override suspend fun refresh(
        scope: ScheduleScope,
        from: LocalDate,
        to: LocalDate
    ): Resource<Unit> = errors.call {
        val accountId = checkNotNull(session.getUserId()) { "Sesi tidak ditemukan." }
        val cacheScope = cacheScope(scope)

        // Jadwal sendiri tanpa parameter; wali murid mengirim studentId, wali kelas classGroupId.
        val studentId = (scope as? ScheduleScope.Child)?.studentId
        val classGroupId = (scope as? ScheduleScope.HomeroomClass)?.classGroupId
        val response = api.getSchedule(from.toString(), to.toString(), studentId, classGroupId)
        check(response.success) { "Jadwal gagal dimuat." }
        val rows = (response.data ?: emptyList()).map { it.toDomain().toEntity(accountId, cacheScope) }

        database.withTransaction {
            dao.deleteSchedulesInRange(accountId, cacheScope, from.toString(), to.toString())
            dao.insertSchedules(rows)
        }
    }

    // Kunci cache: id siswa (siswa/wali murid), 0 (jadwal mengajar guru), -id kelas (kelas binaan).
    private suspend fun cacheScope(scope: ScheduleScope): Int = when (scope) {
        ScheduleScope.Own -> session.getStudentId() ?: 0
        is ScheduleScope.Child -> scope.studentId
        is ScheduleScope.HomeroomClass -> -scope.classGroupId
    }
}
