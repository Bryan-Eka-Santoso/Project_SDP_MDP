package id.sch.santaagnes.sim.ui.task

import id.sch.santaagnes.sim.domain.model.Todo
import id.sch.santaagnes.sim.ui.task.FakeTaskRepository.Companion.task
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StudentTaskViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun taskIn(id: Int, subject: String, day: Int) =
        task(id).copy(subjectName = subject, dueDate = LocalDate.of(2026, 11, day))

    @Test
    fun groupsTasksBySubjectAndOrdersByDueDate() = runTest(dispatcher) {
        val tasks = FakeTaskRepository().apply {
            pages = listOf(listOf(taskIn(1, "Matematika", 25), taskIn(2, "Biologi", 20), taskIn(3, "Matematika", 15)))
        }
        val viewModel = StudentTaskViewModel(tasks, FakeTodoRepository())
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value

        assertEquals(listOf("Biologi", "Matematika"), state.groups.map { it.subjectName })
        assertEquals(listOf(3, 1), state.groups[1].rows.map { it.task.id })
        assertFalse(state.isLoading)
    }

    @Test
    fun loadsEveryPageOfTasks() = runTest(dispatcher) {
        val tasks = FakeTaskRepository().apply {
            pages = listOf(listOf(taskIn(1, "Matematika", 15)), listOf(taskIn(2, "Matematika", 16)))
        }
        val viewModel = StudentTaskViewModel(tasks, FakeTodoRepository())
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        assertEquals(listOf(1, 2), tasks.requestedPages)
        assertEquals(2, viewModel.uiState.value.groups.single().rows.size)
    }

    @Test
    fun doneTaskMovesAfterOpenOnesAndIsMarkedLocally() = runTest(dispatcher) {
        val tasks = FakeTaskRepository().apply {
            pages = listOf(listOf(taskIn(1, "Matematika", 15), taskIn(2, "Matematika", 20)))
        }
        val local = FakeTodoRepository()
        val viewModel = StudentTaskViewModel(tasks, local)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        viewModel.setTaskDone(1, true)

        val rows = viewModel.uiState.value.groups.single().rows
        assertEquals(listOf(2, 1), rows.map { it.task.id })
        assertEquals(listOf(false, true), rows.map { it.done })
        assertEquals(setOf(1), local.doneTaskIds.value)

        viewModel.setTaskDone(1, false)
        assertTrue(local.doneTaskIds.value.isEmpty())
    }

    @Test
    fun serverFailureKeepsRemindersAvailable() = runTest(dispatcher) {
        val tasks = FakeTaskRepository().apply { tasksError = "Tidak dapat terhubung ke server." }
        val local = FakeTodoRepository()
        local.todos.value = listOf(Todo(1, "Belajar", null, LocalDate.of(2026, 11, 20), null, false))
        val viewModel = StudentTaskViewModel(tasks, local)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        val state = viewModel.uiState.value

        assertEquals("Tidak dapat terhubung ke server.", state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.isTasksEmpty)
        assertEquals(1, state.todos.size)
    }

    @Test
    fun emptyTasksIsEmptyState() = runTest(dispatcher) {
        val viewModel = StudentTaskViewModel(FakeTaskRepository(), FakeTodoRepository())
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        assertTrue(viewModel.uiState.value.isTasksEmpty)
    }

    @Test
    fun todoCanBeCompletedAndDeleted() = runTest(dispatcher) {
        val local = FakeTodoRepository()
        local.todos.value = listOf(Todo(1, "Belajar", null, LocalDate.of(2026, 11, 20), null, false))
        val viewModel = StudentTaskViewModel(FakeTaskRepository(), local)
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        viewModel.setTodoCompleted(1, true)
        assertTrue(viewModel.uiState.value.todos.single().isCompleted)

        viewModel.deleteTodo(1)
        assertTrue(viewModel.uiState.value.todos.isEmpty())
    }

    @Test
    fun selectingTabIsReflectedInState() = runTest(dispatcher) {
        val viewModel = StudentTaskViewModel(FakeTaskRepository(), FakeTodoRepository())
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect { } }

        viewModel.selectTab(StudentTaskTab.REMINDERS)

        assertEquals(StudentTaskTab.REMINDERS, viewModel.uiState.value.tab)
    }
}
