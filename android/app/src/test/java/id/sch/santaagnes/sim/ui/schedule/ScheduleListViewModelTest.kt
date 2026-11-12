package id.sch.santaagnes.sim.ui.schedule

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.ScheduleRepository
import id.sch.santaagnes.sim.domain.model.ScheduleEntry
import id.sch.santaagnes.sim.domain.model.ScheduleScope
import id.sch.santaagnes.sim.domain.model.ScheduleViewer
import id.sch.santaagnes.sim.domain.model.ScopeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val week = LocalDate.of(2026, 11, 9) // Senin
    private val tuesday = LocalDate.of(2026, 11, 10)
    private val wednesday = LocalDate.of(2026, 11, 11)

    private class FakeScheduleRepository(
        var viewer: ScheduleViewer = ScheduleViewer(
            role = "STUDENT",
            options = listOf(ScopeOption(ScheduleScope.Own, null))
        )
    ) : ScheduleRepository {
        val cache = MutableStateFlow<List<ScheduleEntry>>(emptyList())
        var refreshResult: Resource<Unit> = Resource.Success(Unit)
        val refreshed = mutableListOf<Pair<ScheduleScope, LocalDate>>()
        val remembered = mutableListOf<ScheduleScope>()

        override suspend fun viewer(): ScheduleViewer = viewer

        override suspend fun remember(scope: ScheduleScope) {
            remembered += scope
        }

        override fun observe(scope: ScheduleScope, from: LocalDate, to: LocalDate): Flow<List<ScheduleEntry>> =
            cache.map { list -> list.filter { it.date in from..to } }

        override suspend fun refresh(scope: ScheduleScope, from: LocalDate, to: LocalDate): Resource<Unit> {
            refreshed += scope to from
            return refreshResult
        }
    }

    private fun lesson(date: LocalDate, slotId: Int, hour: Int) = ScheduleEntry(
        date = date,
        slotId = slotId,
        startTime = LocalTime.of(hour, 0),
        endTime = LocalTime.of(hour + 1, 0),
        subjectId = 2,
        subjectCode = "MAT",
        subjectName = "Matematika",
        teacherName = "Guru Satu",
        roomName = "Ruang 101",
        classGroupName = "X-1",
        change = null,
        original = null
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeScheduleRepository, day: LocalDate = tuesday): ScheduleListViewModel =
        ScheduleListViewModel(repository).also {
            it.showWeek(week)
            it.selectDate(day)
        }

    @Test
    fun showsOnlyLessonsOfSelectedDay() = runTest(dispatcher) {
        val repository = FakeScheduleRepository()
        repository.cache.value = listOf(
            lesson(tuesday, 1, 7),
            lesson(tuesday, 2, 9),
            lesson(wednesday, 3, 7)
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(week, state.weekStart)
        assertEquals(LocalDate.of(2026, 11, 14), state.weekEnd)
        assertEquals(listOf(1, 2), state.lessons.map { it.slotId })

        viewModel.selectDate(wednesday)
        assertEquals(listOf(3), viewModel.uiState.value.lessons.map { it.slotId })
    }

    @Test
    fun ignoresDayOutsideShownWeek() = runTest(dispatcher) {
        val viewModel = viewModel(FakeScheduleRepository())
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        viewModel.selectDate(LocalDate.of(2026, 11, 16))

        assertEquals(tuesday, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun keepsCacheAndShowsMessageWhenRefreshFails() = runTest(dispatcher) {
        val repository = FakeScheduleRepository()
        repository.refreshResult = Resource.Error("Tidak dapat terhubung ke server.")
        repository.cache.value = listOf(lesson(tuesday, 1, 7))
        val viewModel = viewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals("Tidak dapat terhubung ke server.", state.errorMessage)
        assertEquals(1, state.lessons.size)
        assertFalse(state.isEmpty)
    }

    @Test
    fun emptyDayWithoutErrorIsEmptyState() = runTest(dispatcher) {
        val viewModel = viewModel(FakeScheduleRepository())
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value

        assertTrue(state.lessons.isEmpty())
        assertTrue(state.isEmpty)
    }

    @Test
    fun movingBetweenWeeksReloadsFromServer() = runTest(dispatcher) {
        val repository = FakeScheduleRepository()
        val viewModel = viewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        viewModel.nextWeek()
        assertEquals(LocalDate.of(2026, 11, 16), viewModel.uiState.value.weekStart)

        viewModel.previousWeek()
        viewModel.previousWeek()
        assertEquals(LocalDate.of(2026, 11, 2), viewModel.uiState.value.weekStart)
        val weeks = repository.refreshed.map { it.second }
        assertTrue(weeks.containsAll(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 2))))
    }

    @Test
    fun retryClearsErrorWhenServerRecovers() = runTest(dispatcher) {
        val repository = FakeScheduleRepository()
        repository.refreshResult = Resource.Error("Terjadi kesalahan pada server.")
        val viewModel = viewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        assertEquals("Terjadi kesalahan pada server.", viewModel.uiState.value.errorMessage)

        repository.refreshResult = Resource.Success(Unit)
        viewModel.retry()

        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun guardianSwitchesChildAndRemembersIt() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(
            ScheduleViewer(
                role = "GUARDIAN",
                options = listOf(
                    ScopeOption(ScheduleScope.Child(11), "Anak Satu"),
                    ScopeOption(ScheduleScope.Child(12), "Anak Dua")
                ),
                initialIndex = 1
            )
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        // Anak yang terakhir dipilih dibuka lebih dulu.
        assertEquals(1, viewModel.uiState.value.selectedOption)
        assertTrue(repository.refreshed.any { it.first == ScheduleScope.Child(12) })

        viewModel.selectOption(0)

        assertEquals(0, viewModel.uiState.value.selectedOption)
        assertEquals(listOf<ScheduleScope>(ScheduleScope.Child(11)), repository.remembered)
        assertTrue(repository.refreshed.any { it.first == ScheduleScope.Child(11) })
        assertTrue(viewModel.uiState.value.showTeacher)
    }

    @Test
    fun teacherOwnScheduleShowsClassButHomeroomShowsTeacher() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(
            ScheduleViewer(
                role = "TEACHER",
                options = listOf(
                    ScopeOption(ScheduleScope.Own, null),
                    ScopeOption(ScheduleScope.HomeroomClass(5), "X-1")
                )
            )
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        assertFalse(viewModel.uiState.value.showTeacher)

        viewModel.selectOption(1)

        assertTrue(viewModel.uiState.value.showTeacher)
        assertTrue(repository.refreshed.any { it.first == ScheduleScope.HomeroomClass(5) })
    }

    @Test
    fun accountWithoutOptionsDoesNotCallServer() = runTest(dispatcher) {
        val repository = FakeScheduleRepository(ScheduleViewer(role = "GUARDIAN", options = emptyList()))
        val viewModel = viewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value

        assertTrue(repository.refreshed.isEmpty())
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
    }
}
