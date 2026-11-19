package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.domain.model.BillStatus
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.PaymentStatusReportFilter
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportFilter
import id.sch.santaagnes.sim.domain.model.StudentStatsFilter
import id.sch.santaagnes.sim.domain.model.TeachingLoadFilter
import id.sch.santaagnes.sim.ui.admin.FakeReportRepository.Companion.bill
import id.sch.santaagnes.sim.ui.admin.FakeReportRepository.Companion.change
import id.sch.santaagnes.sim.ui.admin.FakeReportRepository.Companion.load
import id.sch.santaagnes.sim.ui.admin.FakeReportRepository.Companion.studentStat
import id.sch.santaagnes.sim.ui.admin.report.PaymentStatusReportViewModel
import id.sch.santaagnes.sim.ui.admin.report.ReportEvent
import id.sch.santaagnes.sim.ui.admin.report.ScheduleChangeReportViewModel
import id.sch.santaagnes.sim.ui.admin.report.StudentStatsReportViewModel
import id.sch.santaagnes.sim.ui.admin.report.TeachingLoadReportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshLoadsFirstPageWithTotalAndOptions() = runTest {
        val repository = FakeReportRepository().apply { changePages = listOf(listOf(change(1), change(2))) }
        val viewModel = ScheduleChangeReportViewModel(repository)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(listOf(1, 2), state.rows.map { it.id })
        assertEquals(2, state.total)
        assertFalse(state.isLoading)
        assertFalse(state.hasMore)
        assertEquals(2, state.options.teachers.size)
        assertEquals(listOf(1 to ScheduleChangeReportFilter()), repository.requestedChanges.map { it.first to it.second })
    }

    @Test
    fun emptyResultIsEmptyState() = runTest {
        val viewModel = ScheduleChangeReportViewModel(FakeReportRepository())

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun errorKeepsMessageAndIsNotEmptyState() = runTest {
        val repository = FakeReportRepository().apply { changesError = "Tidak dapat terhubung ke server." }
        val viewModel = ScheduleChangeReportViewModel(repository)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals("Tidak dapat terhubung ke server.", state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
    }

    @Test
    fun loadMoreAppendsNextPageWithoutDuplicates() = runTest {
        val repository = FakeReportRepository().apply {
            changePages = listOf(listOf(change(1), change(2)), listOf(change(2), change(3)))
        }
        val viewModel = ScheduleChangeReportViewModel(repository)
        viewModel.refresh()
        assertTrue(viewModel.uiState.value.hasMore)

        viewModel.loadMore()

        assertEquals(listOf(1, 2, 3), viewModel.uiState.value.rows.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(listOf(1, 2), repository.requestedChanges.map { it.first })
    }

    @Test
    fun changingFilterReloadsFromFirstPage() = runTest {
        val repository = FakeReportRepository().apply { changePages = listOf(listOf(change(1))) }
        val viewModel = ScheduleChangeReportViewModel(repository)
        viewModel.refresh()

        viewModel.selectType(ChangeType.CANCEL)
        viewModel.selectClassGroup(5)
        viewModel.selectTeacher(3)

        val last = repository.requestedChanges.last()
        assertEquals(1, last.first)
        assertEquals(
            ScheduleChangeReportFilter(classGroupId = 5, teacherId = 3, type = ChangeType.CANCEL),
            last.second
        )
        assertEquals(last.second, viewModel.uiState.value.filter)
    }

    @Test
    fun failedReloadAfterFilterChangeDoesNotKeepOldRows() = runTest {
        val repository = FakeReportRepository().apply { changePages = listOf(listOf(change(1), change(2))) }
        val viewModel = ScheduleChangeReportViewModel(repository)
        viewModel.refresh()
        assertEquals(2, viewModel.uiState.value.rows.size)

        repository.changesError = "Tidak dapat terhubung ke server."
        viewModel.selectType(ChangeType.CANCEL)

        val state = viewModel.uiState.value
        assertTrue(state.rows.isEmpty())
        assertEquals(0, state.total)
        assertEquals("Tidak dapat terhubung ke server.", state.errorMessage)
        assertEquals(ChangeType.CANCEL, state.filter.type)
    }

    @Test
    fun retryAfterErrorLoadsAgain() = runTest {
        val repository = FakeReportRepository().apply {
            changePages = listOf(listOf(change(1)))
            changesError = "Tidak dapat terhubung ke server."
        }
        val viewModel = ScheduleChangeReportViewModel(repository)
        viewModel.refresh()
        assertEquals("Tidak dapat terhubung ke server.", viewModel.uiState.value.errorMessage)

        repository.changesError = null
        viewModel.refresh()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf(1), viewModel.uiState.value.rows.map { it.id })
    }

    @Test
    fun selectingSameFilterDoesNotReload() = runTest {
        val repository = FakeReportRepository()
        val viewModel = ScheduleChangeReportViewModel(repository)
        viewModel.refresh()
        val before = repository.requestedChanges.size

        viewModel.selectType(null)

        assertEquals(before, repository.requestedChanges.size)
    }

    @Test
    fun dateRangeStaysValid() = runTest {
        val viewModel = ScheduleChangeReportViewModel(FakeReportRepository())
        viewModel.selectDateFrom(LocalDate.of(2026, 11, 10))
        viewModel.selectDateTo(LocalDate.of(2026, 11, 20))

        // Awal digeser melewati akhir: akhir ikut pindah.
        viewModel.selectDateFrom(LocalDate.of(2026, 11, 25))
        assertEquals(LocalDate.of(2026, 11, 25), viewModel.uiState.value.filter.dateFrom)
        assertEquals(LocalDate.of(2026, 11, 25), viewModel.uiState.value.filter.dateTo)

        // Akhir dimajukan sebelum awal: awal ikut pindah.
        viewModel.selectDateTo(LocalDate.of(2026, 11, 12))
        assertEquals(LocalDate.of(2026, 11, 12), viewModel.uiState.value.filter.dateFrom)
        assertEquals(LocalDate.of(2026, 11, 12), viewModel.uiState.value.filter.dateTo)

        viewModel.clearDates()
        assertNull(viewModel.uiState.value.filter.dateFrom)
        assertNull(viewModel.uiState.value.filter.dateTo)
    }

    @Test
    fun optionsFailureDoesNotBlockList() = runTest {
        val repository = FakeReportRepository().apply {
            changePages = listOf(listOf(change(1)))
            optionsResult = Resource.Error("Gagal.")
        }
        val viewModel = ScheduleChangeReportViewModel(repository)

        viewModel.refresh()

        assertEquals(1, viewModel.uiState.value.rows.size)
        assertTrue(viewModel.uiState.value.options.teachers.isEmpty())
    }

    @Test
    fun optionsAreLoadedOnlyOnce() = runTest {
        val repository = FakeReportRepository()
        val viewModel = ScheduleChangeReportViewModel(repository)

        viewModel.refresh()
        viewModel.refresh()
        viewModel.selectType(ChangeType.RESCHEDULE)

        assertEquals(1, repository.optionsRequests)
    }

    @Test
    fun exportUsesCurrentFilterAndReportsSaved() = runTest {
        val repository = FakeReportRepository()
        val viewModel = ScheduleChangeReportViewModel(repository)
        viewModel.selectType(ChangeType.SUBSTITUTE)
        val events = mutableListOf<ReportEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.exportTo("content://doc/1")

        assertEquals(
            listOf(ScheduleChangeReportFilter(type = ChangeType.SUBSTITUTE) to "content://doc/1"),
            repository.exportedChanges
        )
        assertEquals(listOf<ReportEvent>(ReportEvent.ExportSaved), events)
        assertFalse(viewModel.uiState.value.isExporting)
        job.cancel()
    }

    @Test
    fun exportReportsWriteFailureAndServerError() = runTest {
        val repository = FakeReportRepository().apply { exportWritten = false }
        val viewModel = ScheduleChangeReportViewModel(repository)
        val events = mutableListOf<ReportEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.exportTo("content://doc/1")
        repository.exportError = "Data melebihi 20000 baris."
        viewModel.exportTo("content://doc/2")

        assertEquals(
            listOf(ReportEvent.ExportWriteFailed, ReportEvent.ExportFailed("Data melebihi 20000 baris.")),
            events
        )
        job.cancel()
    }

    @Test
    fun teachingLoadFiltersByTeacherAndExports() = runTest {
        val repository = FakeReportRepository().apply { loadPages = listOf(listOf(load(3), load(4))) }
        val viewModel = TeachingLoadReportViewModel(repository)
        viewModel.refresh()
        assertEquals(listOf(3, 4), viewModel.uiState.value.rows.map { it.teacherId })
        assertEquals(2, viewModel.uiState.value.total)

        viewModel.selectTeacher(4)
        assertEquals(TeachingLoadFilter(teacherId = 4), repository.requestedLoads.last().second)

        viewModel.exportTo("content://doc/9")
        assertEquals(listOf(TeachingLoadFilter(teacherId = 4) to "content://doc/9"), repository.exportedLoads)
    }

    @Test
    fun paymentStatusFiltersClampDatesAndExport() = runTest {
        val repository = FakeReportRepository().apply {
            paymentPages = listOf(listOf(bill(1, BillStatus.OVERDUE), bill(2, BillStatus.PAID)))
        }
        val viewModel = PaymentStatusReportViewModel(repository)
        viewModel.refresh()
        assertEquals(listOf(1, 2), viewModel.uiState.value.rows.map { it.id })
        assertEquals(2, viewModel.uiState.value.total)

        viewModel.selectClassGroup(5)
        viewModel.selectStatus(BillStatus.OVERDUE)
        val nov20 = LocalDate.of(2026, 11, 20)
        val nov10 = LocalDate.of(2026, 11, 10)
        viewModel.selectDateTo(nov10)
        viewModel.selectDateFrom(nov20)

        assertEquals(
            PaymentStatusReportFilter(classGroupId = 5, status = BillStatus.OVERDUE, dueFrom = nov20, dueTo = nov20),
            viewModel.uiState.value.filter
        )

        viewModel.exportTo("content://doc/bill")
        assertEquals(
            listOf(viewModel.uiState.value.filter to "content://doc/bill"),
            repository.exportedPayments
        )

        viewModel.clearDates()
        assertNull(viewModel.uiState.value.filter.dueFrom)
        assertNull(viewModel.uiState.value.filter.dueTo)
    }

    @Test
    fun studentStatsFiltersByClassAndGradeAndExports() = runTest {
        val repository = FakeReportRepository().apply {
            studentStatPages = listOf(listOf(studentStat(5, "10"), studentStat(6, "11")))
        }
        val viewModel = StudentStatsReportViewModel(repository)
        viewModel.refresh()
        assertEquals(listOf(5, 6), viewModel.uiState.value.rows.map { it.classGroupId })
        assertEquals(listOf("10", "11"), viewModel.uiState.value.options.gradeLevels)

        viewModel.selectGradeLevel("10")
        viewModel.selectClassGroup(5)
        assertEquals(StudentStatsFilter(classGroupId = 5, gradeLevel = "10"), repository.requestedStudentStats.last().second)

        viewModel.exportTo("content://doc/stats")
        assertEquals(
            listOf(StudentStatsFilter(classGroupId = 5, gradeLevel = "10") to "content://doc/stats"),
            repository.exportedStudentStats
        )
    }
}
