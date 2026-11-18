package id.sch.santaagnes.sim.ui.schedule

import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.RescheduleRepository
import id.sch.santaagnes.sim.data.repository.toRequest
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.RescheduleInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class RescheduleFormViewModelTest {

    private class FakeRescheduleRepository : RescheduleRepository {
        var roomsResult: Resource<List<NamedId>> = Resource.Success(emptyList())
        var saveResult: Resource<Unit> = Resource.Success(Unit)
        val saved = mutableListOf<RescheduleInput>()
        val cancelled = mutableListOf<Int>()
        val cancelledClasses = mutableListOf<Triple<Int, LocalDate, String>>()

        override suspend fun rooms(): Resource<List<NamedId>> = roomsResult

        override suspend fun reschedule(input: RescheduleInput): Resource<Unit> {
            saved += input
            return saveResult
        }

        override suspend fun cancelClass(slotId: Int, date: LocalDate, description: String): Resource<Unit> {
            cancelledClasses += Triple(slotId, date, description)
            return saveResult
        }

        override suspend fun cancel(changeId: Int): Resource<Unit> {
            cancelled += changeId
            return saveResult
        }
    }

    // Senin pekan depan (belum lewat kapan pun tes dijalankan) dan Rabu pada pekan yang sama.
    private val monday: LocalDate = DateTimeWib.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1)
    private val wednesday: LocalDate = monday.plusDays(2)
    private val target get() = RescheduleTarget(slotId = 5, date = monday, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(8, 30))
    private val lab = NamedId(102, "Lab Kimia")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadRoomsFillsStateAndFailureShowsMessage() = runTest {
        val repository = FakeRescheduleRepository().apply { roomsResult = Resource.Success(listOf(lab)) }
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.loadRooms()

        assertEquals(listOf(lab), viewModel.uiState.value.rooms)
        assertFalse(viewModel.uiState.value.isLoadingRooms)

        val failing = RescheduleFormViewModel(FakeRescheduleRepository().apply { roomsResult = Resource.Error("Tidak dapat terhubung ke server.") })
        failing.loadRooms()
        assertEquals("Tidak dapat terhubung ke server.", failing.uiState.value.errorMessage)
    }

    @Test
    fun movingToAnotherDayOnlySendsTheNewDate() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.save(target, newDate = wednesday, newStartTime = null, newEndTime = null, room = null, description = " Tukar jam ")

        val input = repository.saved.single()
        assertEquals(RescheduleInput(5, monday, wednesday, null, null, null, " Tukar jam "), input)
        assertEquals(R.string.reschedule_saved, viewModel.done.first())
        // Yang dikirim ke server: tanggal YYYY-MM-DD, keterangan tanpa spasi tepi, selalu RESCHEDULE.
        val request = input.toRequest()
        assertEquals(monday.toString(), request.date)
        assertEquals(wednesday.toString(), request.newDate)
        assertEquals("Tukar jam", request.description)
        assertEquals("RESCHEDULE", request.type)
        assertNull(request.newStartTime)
    }

    @Test
    fun sameDayTimeAndRoomChangeIsAllowedAndSameDateIsNotSentAsMove() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.save(target, newDate = monday, newStartTime = LocalTime.of(10, 0), newEndTime = LocalTime.of(11, 30), room = lab, description = "Praktikum")

        val input = repository.saved.single()
        assertNull(input.newDate)
        assertEquals(102, input.newRoomId)
        assertEquals("10:00", input.toRequest().newStartTime)
        assertEquals("11:30", input.toRequest().newEndTime)
    }

    @Test
    fun nothingChangedOrBlankDescriptionIsRejectedWithoutCallingServer() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)

        // Jam yang sama dengan jadwal asli bukan perubahan.
        viewModel.save(target, newDate = null, newStartTime = LocalTime.of(7, 0), newEndTime = LocalTime.of(8, 30), room = null, description = " ")

        assertEquals(R.string.reschedule_error_nothing, viewModel.uiState.value.dateError)
        assertEquals(R.string.change_error_description, viewModel.uiState.value.descriptionError)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun timeMustBeAPairAndEndAfterStart() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.save(target, newDate = wednesday, newStartTime = LocalTime.of(10, 0), newEndTime = null, room = null, description = "x")
        assertEquals(R.string.change_error_time_pair, viewModel.uiState.value.timeError)

        viewModel.save(target, newDate = wednesday, newStartTime = LocalTime.of(10, 0), newEndTime = LocalTime.of(9, 0), room = null, description = "x")
        assertEquals(R.string.reschedule_error_time_order, viewModel.uiState.value.timeError)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun newDateRulesMatchTheServer() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)
        val save = { date: LocalDate ->
            viewModel.save(target, newDate = date, newStartTime = null, newEndTime = null, room = null, description = "x")
            viewModel.uiState.value.dateError
        }

        assertEquals(R.string.reschedule_error_date_past, save(DateTimeWib.today().minusDays(1)))
        assertEquals(R.string.reschedule_error_sunday, save(monday.plusDays(6)))
        // Senin lain = hari jadwal itu sendiri.
        assertEquals(R.string.reschedule_error_same_weekday, save(monday.plusWeeks(1)))
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun serverErrorIsShownAndFormCanBeSubmittedAgain() = runTest {
        val repository = FakeRescheduleRepository().apply { saveResult = Resource.Error("Perubahan jadwal bentrok dengan jadwal lain di tanggal itu.") }
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.save(target, newDate = wednesday, newStartTime = null, newEndTime = null, room = null, description = "x")

        assertEquals("Perubahan jadwal bentrok dengan jadwal lain di tanggal itu.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun cancelMoveDeletesTheChange() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.cancelMove(40)

        assertEquals(listOf(40), repository.cancelled)
        assertEquals(R.string.reschedule_cancelled, viewModel.done.first())
    }

    @Test
    fun cancelClassNeedsADescriptionThenSendsTheCancellation() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.cancelClass(target, "   ")

        assertEquals(R.string.change_error_description, viewModel.uiState.value.descriptionError)
        assertTrue(repository.cancelledClasses.isEmpty())

        viewModel.cancelClass(target, "Guru sakit")

        assertEquals(listOf(Triple(5, monday, "Guru sakit")), repository.cancelledClasses)
        assertNull(viewModel.uiState.value.descriptionError)
        assertEquals(R.string.reschedule_class_cancelled, viewModel.done.first())
    }

    @Test
    fun checkCancelDescriptionReportsLongText() = runTest {
        val viewModel = RescheduleFormViewModel(FakeRescheduleRepository())

        assertFalse(viewModel.checkCancelDescription("x".repeat(256)))
        assertEquals(R.string.change_error_description_long, viewModel.uiState.value.descriptionError)
        assertTrue(viewModel.checkCancelDescription("Rapat guru"))
        assertNull(viewModel.uiState.value.descriptionError)
    }

    @Test
    fun restoringACancelledClassDeletesTheChangeWithItsOwnMessage() = runTest {
        val repository = FakeRescheduleRepository()
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.cancelMove(41, R.string.reschedule_restored)

        assertEquals(listOf(41), repository.cancelled)
        assertEquals(R.string.reschedule_restored, viewModel.done.first())
    }

    @Test
    fun cancelClassServerErrorIsShown() = runTest {
        val repository = FakeRescheduleRepository().apply { saveResult = Resource.Error("Pertemuan yang sudah lewat tidak dapat dipindah.") }
        val viewModel = RescheduleFormViewModel(repository)

        viewModel.cancelClass(target, "Sakit")

        assertEquals("Pertemuan yang sudah lewat tidak dapat dipindah.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
