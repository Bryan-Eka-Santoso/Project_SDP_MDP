
package id.sch.santaagnes.sim.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isValid: Boolean = false
)

class ChangePasswordViewModel : ViewModel() {

    private val _uiState =
        MutableStateFlow(ChangePasswordUiState())

    val uiState = _uiState.asStateFlow()

    fun validate(
        oldPassword: String,
        newPassword: String,
        confirmPassword: String
    ) {
        viewModelScope.launch {

            val message = when {
                oldPassword.isBlank() ||
                        newPassword.isBlank() ||
                        confirmPassword.isBlank() ->
                    "Semua kolom wajib diisi"

                newPassword.length < 8 ->
                    "Password baru minimal 8 karakter"

                newPassword != confirmPassword ->
                    "Konfirmasi password tidak sama"

                newPassword == oldPassword ->
                    "Password baru harus berbeda"

                else -> null
            }

            _uiState.value = ChangePasswordUiState(
                isValid = message == null,
                message = message
                    ?: "Validasi berhasil. API belum tersedia."
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            message = null
        )
    }
}
