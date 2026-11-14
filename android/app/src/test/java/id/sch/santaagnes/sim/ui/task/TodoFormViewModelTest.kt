package id.sch.santaagnes.sim.ui.task

import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.domain.model.SubjectOption
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoFormViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val tomorrow get() = DateTimeWib.today().plusDays(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyFormShowsErrorsAndSavesNothing() = runTest(dispatcher) {
        val repository = FakeTodoRepository()
        val viewModel = TodoFormViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        viewModel.save(" ", "", null, null)

        val state = viewModel.uiState.value
        assertEquals(R.string.todo_error_title_required, state.titleError)
        assertEquals(R.string.task_error_due_required, state.dueDateError)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun rejectsPastDueDate() = runTest(dispatcher) {
        val repository = FakeTodoRepository()
        val viewModel = TodoFormViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        viewModel.save("Belajar", "", DateTimeWib.today().minusDays(1), null)

        assertEquals(R.string.task_error_due_past, viewModel.uiState.value.dueDateError)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun validReminderIsSavedWithSubjectAndEmitsSaved() = runTest(dispatcher) {
        val repository = FakeTodoRepository()
        val subject = SubjectOption(2, "Matematika")
        repository.subjects.value = listOf(subject)
        val viewModel = TodoFormViewModel(repository)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }
        var saved = false
        backgroundScope.launch(dispatcher) { viewModel.saved.first(); saved = true }

        viewModel.save("Belajar ulangan", "Bab 3", tomorrow, subject)

        assertEquals(listOf(subject), viewModel.uiState.value.subjects)
        assertEquals(1, repository.added.size)
        assertEquals(subject, repository.added[0].subject)
        assertNull(viewModel.uiState.value.titleError)
        assertTrue(saved)
    }
}
