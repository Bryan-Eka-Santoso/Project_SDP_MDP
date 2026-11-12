package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.domain.model.ChangeInput
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.TeacherOption
import id.sch.santaagnes.sim.ui.admin.FakeAdminScheduleRepository.Companion.slot
import id.sch.santaagnes.sim.ui.admin.schedule.ChangeFormViewModel
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
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ChangeFormViewModelTest {

    private val date = LocalDate.of(2026, 11, 16)
    private val teacher = TeacherOption(4, "Guru Dua")
    private val room = FakeAdminScheduleRepository.sampleLookups().rooms.last()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeAdminScheduleRepository = FakeAdminScheduleRepository()) =
        ChangeFormViewModel(repository)

    @Test
    fun loadFillsSlotsAndLookups() = runTest {
        val repository = FakeAdminScheduleRepository().apply { slotPages = listOf(listOf(slot(1))) }
        val viewModel = viewModel(repository)

        viewModel.load()

        val state = viewModel.uiState.value
        assertEquals(1, state.slots.size)
        assertTrue(state.isReady)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadFailureShowsMessageAndIsNotReady() = runTest {
        val repository = FakeAdminScheduleRepository().apply { lookupsResult = Resource.Error("Tidak ada semester aktif.") }
        val viewModel = viewModel(repository)

        viewModel.load()

        assertEquals("Tidak ada semester aktif.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isReady)
    }

    @Test
    fun emptyFormShowsFieldErrorsAndDoesNotCallServer() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = viewModel(repository)

        viewModel.save(null, null, null, "  ", null, null, null, null)

        val state = viewModel.uiState.value
        assertEquals(R.string.change_error_slot, state.slotError)
        assertEquals(R.string.change_error_date, state.dateError)
        assertEquals(R.string.change_error_type, state.typeError)
        assertEquals(R.string.change_error_description, state.descriptionError)
        assertTrue(repository.savedChanges.isEmpty())
    }

    @Test
    fun descriptionOver255IsRejected() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = viewModel(repository)

        viewModel.save(slot(1), date, ChangeType.CANCEL, "a".repeat(256), null, null, null, null)

        assertEquals(R.string.change_error_description_long, viewModel.uiState.value.descriptionError)
        assertTrue(repository.savedChanges.isEmpty())
    }

    @Test
    fun substituteRequiresTeacher() = runTest {
        val viewModel = viewModel()

        viewModel.save(slot(1), date, ChangeType.SUBSTITUTE, "Berhalangan", null, null, null, null)

        assertEquals(R.string.change_error_teacher, viewModel.uiState.value.teacherError)
    }

    @Test
    fun rescheduleNeedsRoomOrTimes() = runTest {
        val viewModel = viewModel()

        viewModel.save(slot(1), date, ChangeType.RESCHEDULE, "Pindah", null, null, null, null)

        assertEquals(R.string.change_error_reschedule, viewModel.uiState.value.rescheduleError)
    }

    @Test
    fun rescheduleTimesMustComeAsPairAndInOrder() = runTest {
        val viewModel = viewModel()

        viewModel.save(slot(1), date, ChangeType.RESCHEDULE, "Pindah", null, null, LocalTime.of(9, 0), null)
        assertEquals(R.string.change_error_time_pair, viewModel.uiState.value.rescheduleError)

        viewModel.save(slot(1), date, ChangeType.RESCHEDULE, "Pindah", null, null, LocalTime.of(9, 0), LocalTime.of(8, 0))
        assertEquals(R.string.slot_error_order, viewModel.uiState.value.rescheduleError)
    }

    @Test
    fun substituteSendsOnlyTeacherAndTrimmedDescription() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = viewModel(repository)

        // Ruangan dan jam sisa dari jenis sebelumnya tidak boleh ikut terkirim.
        viewModel.save(slot(1), date, ChangeType.SUBSTITUTE, "  Berhalangan  ", teacher, room, LocalTime.of(9, 0), LocalTime.of(10, 0))

        assertEquals(
            listOf(ChangeInput(1, date, ChangeType.SUBSTITUTE, "Berhalangan", substituteTeacherId = 4)),
            repository.savedChanges
        )
        viewModel.saved.first()
    }

    @Test
    fun rescheduleSendsRoomAndTimesWithoutTeacher() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = viewModel(repository)

        viewModel.save(slot(1), date, ChangeType.RESCHEDULE, "Pindah ruang", teacher, room, LocalTime.of(9, 0), LocalTime.of(10, 0))

        val sent = repository.savedChanges.single()
        assertNull(sent.substituteTeacherId)
        assertEquals(2, sent.newRoomId)
        assertEquals(LocalTime.of(9, 0), sent.newStartTime)
        assertEquals(LocalTime.of(10, 0), sent.newEndTime)
    }

    @Test
    fun cancelSendsNoExtraFields() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = viewModel(repository)

        viewModel.save(slot(1), date, ChangeType.CANCEL, "Libur", teacher, room, LocalTime.of(9, 0), LocalTime.of(10, 0))

        assertEquals(ChangeInput(1, date, ChangeType.CANCEL, "Libur"), repository.savedChanges.single())
    }

    @Test
    fun serverErrorIsShownAndFormStaysOpen() = runTest {
        val repository = FakeAdminScheduleRepository().apply { saveError = "Tanggal tidak sesuai hari jadwal." }
        val viewModel = viewModel(repository)

        viewModel.save(slot(1), date, ChangeType.CANCEL, "Libur", null, null, null, null)

        assertEquals("Tanggal tidak sesuai hari jadwal.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
