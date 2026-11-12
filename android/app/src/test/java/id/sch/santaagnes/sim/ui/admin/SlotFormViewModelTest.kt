package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.domain.model.SlotInput
import id.sch.santaagnes.sim.ui.admin.schedule.SlotFormViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class SlotFormViewModelTest {

    private val lookups = FakeAdminScheduleRepository.sampleLookups()
    private val assignment = lookups.assignments.first()
    private val room = lookups.rooms.first()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadLookupsFillsState() = runTest {
        val viewModel = SlotFormViewModel(FakeAdminScheduleRepository())

        viewModel.loadLookups()

        assertNotNull(viewModel.uiState.value.lookups)
        assertFalse(viewModel.uiState.value.isLoadingLookups)
    }

    @Test
    fun loadLookupsFailureShowsMessage() = runTest {
        val repository = FakeAdminScheduleRepository().apply {
            lookupsResult = Resource.Error("Tidak ada semester aktif.")
        }
        val viewModel = SlotFormViewModel(repository)

        viewModel.loadLookups()

        assertEquals("Tidak ada semester aktif.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.lookups)
    }

    @Test
    fun emptyFormShowsFieldErrorsAndDoesNotCallServer() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = SlotFormViewModel(repository)

        viewModel.save(null, null, null, null, null, null)

        val state = viewModel.uiState.value
        assertEquals(R.string.slot_error_assignment, state.assignmentError)
        assertEquals(R.string.slot_error_room, state.roomError)
        assertEquals(R.string.slot_error_day, state.dayError)
        assertEquals(R.string.slot_error_start, state.startError)
        assertEquals(R.string.slot_error_end, state.endError)
        assertTrue(repository.createdSlots.isEmpty())
    }

    @Test
    fun endMustBeAfterStart() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = SlotFormViewModel(repository)

        viewModel.save(null, assignment, room, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(8, 0))

        assertEquals(R.string.slot_error_order, viewModel.uiState.value.endError)
        assertTrue(repository.createdSlots.isEmpty())
    }

    @Test
    fun validNewSlotIsCreatedAndEmitsSaved() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = SlotFormViewModel(repository)

        viewModel.save(null, assignment, room, DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(8, 30))

        assertEquals(
            listOf(SlotInput(7, 1, DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(8, 30))),
            repository.createdSlots
        )
        viewModel.saved.first()
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun editUpdatesExistingSlot() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = SlotFormViewModel(repository)

        viewModel.save(42, assignment, room, DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))

        assertEquals(42, repository.updatedSlots.single().first)
        assertTrue(repository.createdSlots.isEmpty())
    }

    @Test
    fun conflictFromServerIsShownAndFormStaysOpen() = runTest {
        val repository = FakeAdminScheduleRepository().apply {
            saveError = "Guru sudah mengajar di kelas lain pada jam ini."
        }
        val viewModel = SlotFormViewModel(repository)

        viewModel.save(null, assignment, room, DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(8, 30))

        assertEquals("Guru sudah mengajar di kelas lain pada jam ini.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
