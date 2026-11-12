package id.sch.santaagnes.sim.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AccountRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val isSaving: Boolean = false,
    // galat dari server (password lama salah, jaringan); galat per field ada di *Error di bawah
    val errorMessage: String? = null,
    @param:StringRes val currentError: Int? = null,
    @param:StringRes val newError: Int? = null,
    @param:StringRes val confirmError: Int? = null
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val repository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Dipancarkan sekali setelah password berhasil diubah di server. */
    val saved = savedChannel.receiveAsFlow()

    fun save(current: String, new: String, confirm: String) {
        if (_uiState.value.isSaving) return

        val state = _uiState.value.copy(
            errorMessage = null,
            currentError = if (current.isEmpty()) R.string.password_error_current_required else null,
            newError = when {
                new.length < MIN_LENGTH -> R.string.password_error_too_short
                // bcrypt di server hanya membaca 72 byte pertama; yang lebih panjang ditolak
                new.toByteArray(Charsets.UTF_8).size > MAX_BYTES -> R.string.password_error_too_long
                new == current -> R.string.password_error_same
                else -> null
            },
            confirmError = if (confirm != new) R.string.password_error_mismatch else null
        )
        if (state.currentError != null || state.newError != null || state.confirmError != null) {
            _uiState.value = state
            return
        }

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            when (val result = repository.changePassword(current, new)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    savedChannel.send(Unit)
                }
                // Password lama salah dibalas 400 dengan pesan Indonesia dari server.
                is Resource.Error -> _uiState.update { it.copy(isSaving = false, errorMessage = result.message) }
            }
        }
    }

    private companion object {
        const val MIN_LENGTH = 8
        const val MAX_BYTES = 72
    }
}
