package id.sch.santaagnes.sim.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
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

            _uiState.value = HomeUiState(
                result = when (val result = repository.getProfile()) {
                    is Resource.Success ->
                        "Sesi valid: ${result.data.name} (${result.data.role})"
                    is Resource.Error ->
                        "Gagal: ${result.message}"
                }
            )
        }
    }
}
