
package id.sch.santaagnes.sim.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val result: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    fun checkSession() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)

            val result = repository.getProfile()

            _uiState.value = HomeUiState(
                result = result.fold(
                    onSuccess = {
                        "Sesi valid: ${it.name} (${it.role})"
                    },
                    onFailure = {
                        "Gagal: ${it.message}"
                    }
                )
            )
        }
    }
}
