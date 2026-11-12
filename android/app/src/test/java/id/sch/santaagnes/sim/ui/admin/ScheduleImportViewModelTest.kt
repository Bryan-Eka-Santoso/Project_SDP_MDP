package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.ui.admin.schedule.ScheduleImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ScheduleImportViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importWithoutFileDoesNothing() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = ScheduleImportViewModel(repository)

        viewModel.import()

        assertTrue(repository.imports.isEmpty())
        assertFalse(viewModel.uiState.value.canImport)
    }

    @Test
    fun pickingFileShowsNameAndEnablesImport() = runTest {
        val viewModel = ScheduleImportViewModel(FakeAdminScheduleRepository())

        viewModel.onFilePicked("content://doc/1")

        assertEquals("jadwal.xlsx", viewModel.uiState.value.fileName)
        assertTrue(viewModel.uiState.value.canImport)
    }

    @Test
    fun successShowsImportedCount() = runTest {
        val repository = FakeAdminScheduleRepository().apply { importedCount = 42 }
        val viewModel = ScheduleImportViewModel(repository)
        viewModel.onFilePicked("content://doc/1")

        viewModel.import()

        assertEquals(listOf("content://doc/1"), repository.imports)
        assertEquals(42, viewModel.uiState.value.importedCount)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun rejectedImportShowsRowErrorsAndNoCount() = runTest {
        val message = "Baris 3, kolom Hari: Hari tidak dikenal.\nBaris 5: Guru tidak ditemukan."
        val repository = FakeAdminScheduleRepository().apply { importError = message }
        val viewModel = ScheduleImportViewModel(repository)
        viewModel.onFilePicked("content://doc/1")

        viewModel.import()

        assertEquals(message, viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.importedCount)
        assertFalse(viewModel.uiState.value.isUploading)
    }

    @Test
    fun pickingNewFileClearsPreviousResult() = runTest {
        val repository = FakeAdminScheduleRepository().apply { importedCount = 5 }
        val viewModel = ScheduleImportViewModel(repository)
        viewModel.onFilePicked("content://doc/1")
        viewModel.import()

        viewModel.onFilePicked("content://doc/2")

        assertNull(viewModel.uiState.value.importedCount)
        assertEquals("content://doc/2", viewModel.uiState.value.fileUri)
    }
}
