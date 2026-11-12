package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.domain.model.ImportRowError
import id.sch.santaagnes.sim.ui.admin.schedule.ScheduleImportViewModel
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

    @Test
    fun rejectedImportKeepsPerRowErrors() = runTest {
        val rows = listOf(
            ImportRowError(row = 3, column = "Hari", message = "Hari tidak dikenal."),
            ImportRowError(row = 3, column = "Jam Selesai", message = "Jam selesai harus setelah jam mulai."),
            ImportRowError(row = 7, column = null, message = "Bentrok kelas.")
        )
        val repository = FakeAdminScheduleRepository().apply {
            importError = "Berkas tidak valid."
            importErrorRows = rows
        }
        val viewModel = ScheduleImportViewModel(repository)
        viewModel.onFilePicked("content://doc/1")

        viewModel.import()

        assertEquals(rows, viewModel.uiState.value.importErrors)
        assertEquals(2, viewModel.uiState.value.errorRowCount)
    }

    @Test
    fun successClearsPreviousRowErrors() = runTest {
        val repository = FakeAdminScheduleRepository().apply {
            importError = "Berkas tidak valid."
            importErrorRows = listOf(ImportRowError(3, "Hari", "Hari tidak dikenal."))
        }
        val viewModel = ScheduleImportViewModel(repository)
        viewModel.onFilePicked("content://doc/1")
        viewModel.import()

        repository.importError = null
        repository.importedCount = 3
        viewModel.import()

        assertTrue(viewModel.uiState.value.importErrors.isEmpty())
        assertEquals(3, viewModel.uiState.value.importedCount)
    }

    @Test
    fun templateSaveReportsSuccessAndFailure() = runTest {
        val repository = FakeAdminScheduleRepository()
        val viewModel = ScheduleImportViewModel(repository)
        val events = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.templateSaved.collect { events += it } }

        viewModel.onTemplateDestinationPicked("content://dest/1")
        repository.templateSaveResult = false
        viewModel.onTemplateDestinationPicked("content://dest/2")
        job.cancel()

        assertEquals(listOf("content://dest/1", "content://dest/2"), repository.savedTemplates)
        assertEquals(listOf(true, false), events)
    }
}
