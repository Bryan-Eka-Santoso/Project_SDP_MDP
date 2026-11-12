package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.ReportRepository
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

class FakeReportRepository : ReportRepository {
    var changePages: List<List<ScheduleChangeReportRow>> = listOf(emptyList())
    var loadPages: List<List<TeachingLoadRow>> = listOf(emptyList())
    var changesError: String? = null
    var loadError: String? = null
    var exportError: String? = null
    var exportWritten = true
    var optionsResult: Resource<ReportOptions> = Resource.Success(sampleOptions())

    val requestedChanges = mutableListOf<Pair<Int, ScheduleChangeReportFilter>>()
    val requestedLoads = mutableListOf<Pair<Int, TeachingLoadFilter>>()
    val exportedChanges = mutableListOf<Pair<ScheduleChangeReportFilter, String>>()
    val exportedLoads = mutableListOf<Pair<TeachingLoadFilter, String>>()
    var optionsRequests = 0

    override suspend fun scheduleChanges(
        page: Int,
        filter: ScheduleChangeReportFilter
    ): Resource<ReportPage<ScheduleChangeReportRow>> {
        requestedChanges += page to filter
        changesError?.let { return Resource.Error(it) }
        val rows = changePages.getOrElse(page - 1) { emptyList() }
        return Resource.Success(ReportPage(rows, page, page < changePages.size, changePages.sumOf { it.size }))
    }

    override suspend fun teachingLoad(page: Int, filter: TeachingLoadFilter): Resource<ReportPage<TeachingLoadRow>> {
        requestedLoads += page to filter
        loadError?.let { return Resource.Error(it) }
        val rows = loadPages.getOrElse(page - 1) { emptyList() }
        return Resource.Success(ReportPage(rows, page, page < loadPages.size, loadPages.sumOf { it.size }))
    }

    override suspend fun exportScheduleChanges(
        filter: ScheduleChangeReportFilter,
        destination: String
    ): Resource<Boolean> {
        exportError?.let { return Resource.Error(it) }
        exportedChanges += filter to destination
        return Resource.Success(exportWritten)
    }

    override suspend fun exportTeachingLoad(filter: TeachingLoadFilter, destination: String): Resource<Boolean> {
        exportError?.let { return Resource.Error(it) }
        exportedLoads += filter to destination
        return Resource.Success(exportWritten)
    }

    override suspend fun options(): Resource<ReportOptions> {
        optionsRequests += 1
        return optionsResult
    }

    companion object {
        fun change(id: Int, type: ChangeType = ChangeType.CANCEL) = ScheduleChangeReportRow(
            id = id,
            date = LocalDate.of(2026, 11, 14),
            dayOfWeek = DayOfWeek.SATURDAY,
            classGroupName = "X-1",
            subjectName = "Fisika",
            originalTeacherName = "Guru Satu",
            type = type,
            substituteTeacherName = null,
            originalRoomName = "Ruang X-1",
            newRoomName = null,
            originalStart = LocalTime.of(10, 30),
            originalEnd = LocalTime.of(12, 0),
            newStart = null,
            newEnd = null,
            description = "Guru berhalangan."
        )

        fun load(teacherId: Int) = TeachingLoadRow(
            teacherId = teacherId,
            teacherName = "Guru $teacherId",
            employeeNumber = "19800000$teacherId",
            sessionsPerWeek = 12,
            totalHours = 18.0,
            classCount = 2,
            subjectNames = listOf("Matematika"),
            hoursPerDay = listOf(3.0, 3.0, 3.0, 3.0, 3.0, 3.0)
        )

        fun sampleOptions() = ReportOptions(
            teachers = listOf(TeacherOption(3, "Guru Satu"), TeacherOption(4, "Guru Dua")),
            classGroups = listOf(NamedId(5, "X-1"), NamedId(6, "X-2"))
        )
    }
}
