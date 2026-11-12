package id.sch.santaagnes.sim.ui.task

import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class TeacherTaskFormViewModelTest {

    private val assignment = TeachingAssignment(id = 7, subjectName = "Matematika", classGroupName = "X-1")
    private val tomorrow get() = DateTimeWib.today().plusDays(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadAssignmentsFillsState() = runTest {
        val repository = FakeTaskRepository().apply { assignmentsResult = Resource.Success(listOf(assignment)) }
        val viewModel = TeacherTaskFormViewModel(repository)

        viewModel.loadAssignments()

        assertEquals(listOf(assignment), viewModel.uiState.value.assignments)
        assertFalse(viewModel.uiState.value.isLoadingAssignments)
    }

    @Test
    fun loadAssignmentsFailureShowsMessage() = runTest {
        val repository = FakeTaskRepository().apply { assignmentsResult = Resource.Error("Tidak dapat terhubung ke server.") }
        val viewModel = TeacherTaskFormViewModel(repository)

        viewModel.loadAssignments()

        assertEquals("Tidak dapat terhubung ke server.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun emptyFormShowsFieldErrorsAndDoesNotCallServer() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = TeacherTaskFormViewModel(repository)

        viewModel.save(taskId = null, assignment = null, title = "  ", description = "", dueDate = null)

        val state = viewModel.uiState.value
        assertEquals(R.string.task_error_assignment, state.assignmentError)
        assertEquals(R.string.task_error_title_required, state.titleError)
        assertEquals(R.string.task_error_due_required, state.dueDateError)
        assertTrue(repository.created.isEmpty())
        assertFalse(state.isSaving)
    }

    @Test
    fun rejectsTooLongTitleAndNotes() = runTest {
        val viewModel = TeacherTaskFormViewModel(FakeTaskRepository())

        viewModel.save(null, assignment, "a".repeat(151), "b".repeat(2001), tomorrow)

        assertEquals(R.string.task_error_title_long, viewModel.uiState.value.titleError)
        assertEquals(R.string.task_error_description_long, viewModel.uiState.value.descriptionError)
    }

    @Test
    fun newTaskRejectsPastDueDate() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = TeacherTaskFormViewModel(repository)

        viewModel.save(null, assignment, "Latihan", "", DateTimeWib.today().minusDays(1))

        assertEquals(R.string.task_error_due_past, viewModel.uiState.value.dueDateError)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun editingAllowsPastDueDate() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = TeacherTaskFormViewModel(repository)

        viewModel.save(5, null, "Latihan", "", DateTimeWib.today().minusDays(3))

        assertNull(viewModel.uiState.value.dueDateError)
        assertEquals(listOf(5), repository.updated.map { it.first })
    }

    @Test
    fun validNewTaskIsCreatedAndEmitsSaved() = runTest {
        val repository = FakeTaskRepository()
        val viewModel = TeacherTaskFormViewModel(repository)
        var saved = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.saved.first(); saved = true }

        viewModel.save(null, assignment, " Latihan Bab 3 ", "Soal 1-10", tomorrow)

        assertEquals(1, repository.created.size)
        assertEquals(7, repository.created[0].first)
        assertEquals(tomorrow, repository.created[0].second.dueDate)
        assertTrue(saved)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun serverErrorIsShownAndFormStaysOpen() = runTest {
        val repository = FakeTaskRepository().apply { saveError = "Tenggat tidak boleh sebelum hari ini." }
        val viewModel = TeacherTaskFormViewModel(repository)

        viewModel.save(null, assignment, "Latihan", "", tomorrow)

        assertEquals("Tenggat tidak boleh sebelum hari ini.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
