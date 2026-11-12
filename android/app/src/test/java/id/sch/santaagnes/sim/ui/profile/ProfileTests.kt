package id.sch.santaagnes.sim.ui.profile

import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AccountRepository
import id.sch.santaagnes.sim.domain.model.Profile
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

class FakeAccountRepository : AccountRepository {
    var cached: Profile? = Profile("Siswa Satu", null, "STUDENT", null)
    var refreshResult: Resource<Profile> =
        Resource.Success(Profile("Siswa Satu", "siswa1@santaagnes.test", "STUDENT", "2026001"))
    var changeError: String? = null
    val changes = mutableListOf<Pair<String, String>>()

    override suspend fun cachedProfile(): Profile? = cached

    override suspend fun refreshProfile(): Resource<Profile> = refreshResult

    override suspend fun changePassword(currentPassword: String, newPassword: String): Resource<Unit> {
        changeError?.let { return Resource.Error(it) }
        changes += currentPassword to newPassword
        return Resource.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun serverProfileReplacesCachedOne() = runTest {
        val viewModel = ProfileViewModel(FakeAccountRepository())

        val state = viewModel.uiState.value
        assertEquals("siswa1@santaagnes.test", state.profile?.email)
        assertEquals("2026001", state.profile?.identifier)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun failedRefreshKeepsCachedProfileAndShowsMessage() = runTest {
        val repository = FakeAccountRepository().apply { refreshResult = Resource.Error("Tidak dapat terhubung ke server.") }
        val viewModel = ProfileViewModel(repository)

        val state = viewModel.uiState.value
        assertEquals("Siswa Satu", state.profile?.name)
        assertNull(state.profile?.email)
        assertEquals("Tidak dapat terhubung ke server.", state.errorMessage)
    }

    @Test
    fun billsEntryDependsOnRoleAndScope() {
        assertTrue(Profile("A", null, "STUDENT", null).canViewBills)
        assertTrue(Profile("A", null, "ADMIN", null).canViewBills)
        assertTrue(Profile("A", null, "GUARDIAN", null, children = listOf("Anak")).canViewBills)
        assertFalse(Profile("A", null, "GUARDIAN", null).canViewBills)
        assertTrue(Profile("A", null, "TEACHER", null, homeroomClasses = listOf("X-1")).canViewBills)
        assertFalse(Profile("A", null, "TEACHER", null).canViewBills)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyFormShowsFieldErrorsAndDoesNotCallServer() = runTest {
        val repository = FakeAccountRepository()
        val viewModel = ChangePasswordViewModel(repository)

        viewModel.save("", "", "x")

        val state = viewModel.uiState.value
        assertEquals(R.string.password_error_current_required, state.currentError)
        assertEquals(R.string.password_error_too_short, state.newError)
        assertEquals(R.string.password_error_mismatch, state.confirmError)
        assertTrue(repository.changes.isEmpty())
    }

    @Test
    fun newPasswordMustBeAtLeastEightCharacters() = runTest {
        val viewModel = ChangePasswordViewModel(FakeAccountRepository())

        viewModel.save("lama1234", "pendek7", "pendek7")

        assertEquals(R.string.password_error_too_short, viewModel.uiState.value.newError)
    }

    @Test
    fun newPasswordOver72BytesIsRejected() = runTest {
        val viewModel = ChangePasswordViewModel(FakeAccountRepository())
        // 37 huruf "é" = 74 byte dalam UTF-8 walau hanya 37 karakter
        val long = "é".repeat(37)

        viewModel.save("lama1234", long, long)

        assertEquals(R.string.password_error_too_long, viewModel.uiState.value.newError)
    }

    @Test
    fun newPasswordMustDifferFromCurrent() = runTest {
        val viewModel = ChangePasswordViewModel(FakeAccountRepository())

        viewModel.save("sama12345", "sama12345", "sama12345")

        assertEquals(R.string.password_error_same, viewModel.uiState.value.newError)
    }

    @Test
    fun confirmationMustMatch() = runTest {
        val viewModel = ChangePasswordViewModel(FakeAccountRepository())

        viewModel.save("lama1234", "baru12345", "baru12346")

        assertEquals(R.string.password_error_mismatch, viewModel.uiState.value.confirmError)
    }

    @Test
    fun validChangeIsSentAndEmitsSaved() = runTest {
        val repository = FakeAccountRepository()
        val viewModel = ChangePasswordViewModel(repository)

        viewModel.save("lama1234", "baru12345", "baru12345")

        assertEquals(listOf("lama1234" to "baru12345"), repository.changes)
        viewModel.saved.first()
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun wrongCurrentPasswordFromServerIsShown() = runTest {
        val repository = FakeAccountRepository().apply { changeError = "Password lama salah." }
        val viewModel = ChangePasswordViewModel(repository)

        viewModel.save("salah1234", "baru12345", "baru12345")

        assertEquals("Password lama salah.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
