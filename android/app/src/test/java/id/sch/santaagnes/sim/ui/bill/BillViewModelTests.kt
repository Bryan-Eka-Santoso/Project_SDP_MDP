package id.sch.santaagnes.sim.ui.bill

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.BillRepository
import id.sch.santaagnes.sim.domain.model.Bill
import id.sch.santaagnes.sim.domain.model.BillFilter
import id.sch.santaagnes.sim.domain.model.BillImportResult
import id.sch.santaagnes.sim.domain.model.BillScope
import id.sch.santaagnes.sim.domain.model.BillScopeOption
import id.sch.santaagnes.sim.domain.model.BillStatus
import id.sch.santaagnes.sim.domain.model.BillViewer
import id.sch.santaagnes.sim.domain.model.PageResult
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
import java.time.LocalDate

class FakeBillRepository : BillRepository {
    var viewer = BillViewer("STUDENT", listOf(BillScopeOption(BillScope.Student(41), null)))
    var pages: List<List<Bill>> = listOf(emptyList())
    var billsError: String? = null
    var importError: String? = null
    var importResult = BillImportResult(created = 2, updated = 1, unchanged = 0, notifiedStudents = 1)
    var fileName: String? = "pembayaran.xlsx"

    val requests = mutableListOf<List<Any?>>()
    val remembered = mutableListOf<BillScope>()
    val imports = mutableListOf<String>()

    override suspend fun viewer(): BillViewer = viewer

    override suspend fun remember(scope: BillScope) {
        remembered += scope
    }

    override suspend fun bills(scope: BillScope, page: Int, filter: BillFilter, query: String?): Resource<PageResult<Bill>> {
        requests += listOf(scope, page, filter, query)
        billsError?.let { return Resource.Error(it) }
        return Resource.Success(PageResult(pages.getOrElse(page - 1) { emptyList() }, page, page < pages.size))
    }

    override suspend fun fileName(uri: String): String? = fileName

    override suspend fun importBills(uri: String): Resource<BillImportResult> {
        importError?.let { return Resource.Error(it) }
        imports += uri
        return Resource.Success(importResult)
    }

    companion object {
        fun bill(id: Int, status: BillStatus = BillStatus.UNPAID) = Bill(
            id = id,
            code = "SPP-$id",
            paymentType = "SPP",
            amount = 850_000,
            dueDate = LocalDate.of(2026, 11, 15),
            paidDate = null,
            status = status,
            student = null
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillListViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsFirstPageForStudent() = runTest {
        val repository = FakeBillRepository().apply { pages = listOf(listOf(FakeBillRepository.bill(1))) }
        val viewModel = BillListViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals(listOf(1), state.bills.map { it.id })
        assertFalse(state.isLoading)
        assertFalse(state.showStudent)
        assertFalse(state.canImport)
    }

    @Test
    fun emptyListIsEmptyState() = runTest {
        val viewModel = BillListViewModel(FakeBillRepository())

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun accountWithoutScopesHasNoAccessAndNeverCallsServer() = runTest {
        val repository = FakeBillRepository().apply { viewer = BillViewer("TEACHER", emptyList()) }
        val viewModel = BillListViewModel(repository)

        assertTrue(viewModel.uiState.value.hasNoAccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isEmpty)
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun failureShowsMessage() = runTest {
        val repository = FakeBillRepository().apply { billsError = "Tidak dapat terhubung ke server." }
        val viewModel = BillListViewModel(repository)

        assertEquals("Tidak dapat terhubung ke server.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun loadMoreAppendsWithoutDuplicates() = runTest {
        val repository = FakeBillRepository().apply {
            pages = listOf(listOf(FakeBillRepository.bill(1), FakeBillRepository.bill(2)), listOf(FakeBillRepository.bill(2), FakeBillRepository.bill(3)))
        }
        val viewModel = BillListViewModel(repository)

        viewModel.loadMore()

        assertEquals(listOf(1, 2, 3), viewModel.uiState.value.bills.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun changingFilterReloadsFromFirstPage() = runTest {
        val repository = FakeBillRepository()
        val viewModel = BillListViewModel(repository)

        viewModel.selectFilter(BillFilter.OVERDUE)

        assertEquals(listOf(BillScope.Student(41), 1, BillFilter.OVERDUE, ""), repository.requests.last())
    }

    @Test
    fun guardianSwitchingChildReloadsAndRemembersChoice() = runTest {
        val repository = FakeBillRepository().apply {
            viewer = BillViewer(
                "GUARDIAN",
                listOf(
                    BillScopeOption(BillScope.Student(41), "Anak Satu"),
                    BillScopeOption(BillScope.Student(42), "Anak Dua")
                )
            )
        }
        val viewModel = BillListViewModel(repository)

        viewModel.selectOption(1)

        assertEquals(BillScope.Student(42), repository.requests.last()[0])
        assertEquals(listOf<BillScope>(BillScope.Student(42)), repository.remembered)
    }

    @Test
    fun adminSearchSendsTrimmedQueryAndShowsStudents() = runTest {
        val repository = FakeBillRepository().apply { viewer = BillViewer("ADMIN", listOf(BillScopeOption(BillScope.All, null))) }
        val viewModel = BillListViewModel(repository)

        viewModel.search("  Budi ")

        assertEquals("Budi", repository.requests.last()[3])
        assertTrue(viewModel.uiState.value.showStudent)
        assertTrue(viewModel.uiState.value.canImport)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillImportViewModelTest {

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
        val repository = FakeBillRepository()
        val viewModel = BillImportViewModel(repository)

        viewModel.import()

        assertTrue(repository.imports.isEmpty())
        assertFalse(viewModel.uiState.value.canImport)
    }

    @Test
    fun successShowsSummary() = runTest {
        val repository = FakeBillRepository()
        val viewModel = BillImportViewModel(repository)
        viewModel.onFilePicked("content://doc/1")

        viewModel.import()

        assertEquals("pembayaran.xlsx", viewModel.uiState.value.fileName)
        assertEquals(BillImportResult(2, 1, 0, 1), viewModel.uiState.value.result)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun rejectedImportShowsRowErrorsAndNoResult() = runTest {
        val message = "Baris 4, kolom NIS: NIS tidak ditemukan."
        val repository = FakeBillRepository().apply { importError = message }
        val viewModel = BillImportViewModel(repository)
        viewModel.onFilePicked("content://doc/1")

        viewModel.import()

        assertEquals(message, viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isUploading)
    }

    @Test
    fun pickingNewFileClearsPreviousResult() = runTest {
        val viewModel = BillImportViewModel(FakeBillRepository())
        viewModel.onFilePicked("content://doc/1")
        viewModel.import()

        viewModel.onFilePicked("content://doc/2")

        assertNull(viewModel.uiState.value.result)
        assertEquals("content://doc/2", viewModel.uiState.value.fileUri)
    }
}
