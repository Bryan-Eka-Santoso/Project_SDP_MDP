package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.ExportFiles
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.MasterLookupApi
import id.sch.santaagnes.sim.data.remote.ReportApi
import id.sch.santaagnes.sim.data.remote.ScheduleChangeReportDto
import id.sch.santaagnes.sim.data.remote.TeachingLoadDto
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.ReportOptions
import id.sch.santaagnes.sim.domain.model.ReportPage
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportFilter
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportRow
import id.sch.santaagnes.sim.domain.model.TeacherOption
import id.sch.santaagnes.sim.domain.model.TeachingLoadFilter
import id.sch.santaagnes.sim.domain.model.TeachingLoadRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Laporan admin (F-LAP-07, F-LAP-09) dan ekspor Excel (F-LAP-11). Hanya online; backend membatasi ke ADMIN. */
interface ReportRepository {

    suspend fun scheduleChanges(
        page: Int,
        filter: ScheduleChangeReportFilter
    ): Resource<ReportPage<ScheduleChangeReportRow>>

    suspend fun teachingLoad(page: Int, filter: TeachingLoadFilter): Resource<ReportPage<TeachingLoadRow>>

    /** Success(true) = berkas tersimpan di [destination]; Success(false) = server menjawab tetapi berkas gagal ditulis. */
    suspend fun exportScheduleChanges(filter: ScheduleChangeReportFilter, destination: String): Resource<Boolean>

    suspend fun exportTeachingLoad(filter: TeachingLoadFilter, destination: String): Resource<Boolean>

    /** Pilihan guru dan kelas untuk filter. */
    suspend fun options(): Resource<ReportOptions>
}

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val api: ReportApi,
    private val master: MasterLookupApi,
    private val errors: ApiErrorMapper,
    private val files: ExportFiles
) : ReportRepository {

    override suspend fun scheduleChanges(
        page: Int,
        filter: ScheduleChangeReportFilter
    ): Resource<ReportPage<ScheduleChangeReportRow>> = errors.call {
        val response = api.getScheduleChanges(
            page = page,
            limit = PAGE_SIZE,
            classGroupId = filter.classGroupId,
            teacherId = filter.teacherId,
            type = filter.type?.name,
            dateFrom = filter.dateFrom?.toString(),
            dateTo = filter.dateTo?.toString()
        )
        check(response.success) { "Laporan gagal dimuat." }
        ReportPage(
            items = response.data.orEmpty().map { it.toDomain() },
            page = page,
            hasMore = page < (response.meta?.totalPages ?: page),
            total = response.meta?.total ?: response.data.orEmpty().size
        )
    }

    override suspend fun teachingLoad(
        page: Int,
        filter: TeachingLoadFilter
    ): Resource<ReportPage<TeachingLoadRow>> = errors.call {
        val response = api.getTeachingLoad(page = page, limit = PAGE_SIZE, teacherId = filter.teacherId)
        check(response.success) { "Laporan gagal dimuat." }
        ReportPage(
            items = response.data.orEmpty().map { it.toDomain() },
            page = page,
            hasMore = page < (response.meta?.totalPages ?: page),
            total = response.meta?.total ?: response.data.orEmpty().size
        )
    }

    override suspend fun exportScheduleChanges(
        filter: ScheduleChangeReportFilter,
        destination: String
    ): Resource<Boolean> = errors.call {
        val body = api.exportScheduleChanges(
            classGroupId = filter.classGroupId,
            teacherId = filter.teacherId,
            type = filter.type?.name,
            dateFrom = filter.dateFrom?.toString(),
            dateTo = filter.dateTo?.toString()
        )
        files.write(body, destination)
    }

    override suspend fun exportTeachingLoad(filter: TeachingLoadFilter, destination: String): Resource<Boolean> =
        errors.call {
            files.write(api.exportTeachingLoad(filter.teacherId), destination)
        }

    override suspend fun options(): Resource<ReportOptions> = errors.call {
        val semesterId = checkNotNull(master.getActiveSemester().data) { "Tidak ada semester aktif." }.id

        val teachers = mutableListOf<TeacherOption>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response = master.getTeachers(page, LOOKUP_PAGE_SIZE)
            teachers += response.data.orEmpty().map { TeacherOption(it.id, it.user.name) }
            if (page >= (response.meta?.totalPages ?: page)) break
            page += 1
        }

        val classGroups = mutableListOf<NamedId>()
        page = 1
        while (page <= MAX_PAGES) {
            val response = master.getTeachingAssignments(page, LOOKUP_PAGE_SIZE, semesterId)
            classGroups += response.data.orEmpty().map { NamedId(it.classGroup.id, it.classGroup.name) }
            if (page >= (response.meta?.totalPages ?: page)) break
            page += 1
        }

        ReportOptions(
            teachers = teachers.distinctBy { it.id }.sortedBy { it.name },
            classGroups = classGroups.distinctBy { it.id }.sortedBy { it.name }
        )
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val LOOKUP_PAGE_SIZE = 100
        const val MAX_PAGES = 20
    }
}

private const val MINUTES_PER_HOUR = 60.0

// Senin sampai Sabtu: urutan batang jam per hari.
private val WEEK_DAYS = DayOfWeek.entries.take(6)

internal fun ScheduleChangeReportDto.toDomain() = ScheduleChangeReportRow(
    id = id,
    date = LocalDate.parse(date),
    dayOfWeek = DayOfWeek.valueOf(dayOfWeek),
    classGroupName = classGroup.name,
    subjectName = subject.name,
    originalTeacherName = originalTeacher.name,
    type = ChangeType.valueOf(type),
    substituteTeacherName = substituteTeacher?.name,
    originalRoomName = originalRoom?.name,
    newRoomName = newRoom?.name,
    originalStart = LocalTime.parse(originalStartTime),
    originalEnd = LocalTime.parse(originalEndTime),
    newStart = newStartTime?.let(LocalTime::parse),
    newEnd = newEndTime?.let(LocalTime::parse),
    description = description
)

internal fun TeachingLoadDto.toDomain() = TeachingLoadRow(
    teacherId = teacher.id,
    teacherName = teacher.name,
    employeeNumber = teacher.employeeNumber,
    sessionsPerWeek = sessionsPerWeek,
    totalHours = totalHours,
    classCount = classCount,
    subjectNames = subjects.map { it.name },
    hoursPerDay = WEEK_DAYS.map { day -> (perDay[day.name]?.minutes ?: 0.0) / MINUTES_PER_HOUR }
)
