package id.sch.santaagnes.sim.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(LoginUiState())

    val uiState: StateFlow<LoginUiState> =
        _uiState.asStateFlow()

    fun login(
        email: String,
        password: String
    ) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState(
                errorRes = R.string.login_error_required
            )
            return
        }

        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.value = LoginUiState(
                isLoading = true
            )

            _uiState.value = when (val result = repository.login(email.trim(), password)) {
                is Resource.Success -> LoginUiState(
                    isLoginSuccess = true,
                    role = result.data.role
                )
                is Resource.Error -> LoginUiState(
                    errorMessage = result.message
                )
            }
        }
    }
}
