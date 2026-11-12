package id.sch.santaagnes.sim.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AccountRepository
import id.sch.santaagnes.sim.domain.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    // null sampai data sesi terbaca
    val profile: Profile? = null,
    val isRefreshing: Boolean = false,
    // gagal memuat dari server; data sesi tetap tampil
    val errorMessage: String? = null
)

/** Profil tampil dulu dari sesi tersimpan, lalu dilengkapi (email, NIS/NIP) dari server. */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.cachedProfile()?.let { cached -> _uiState.update { it.copy(profile = cached) } }
            refresh()
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            when (val result = repository.refreshProfile()) {
                is Resource.Success -> _uiState.update { it.copy(profile = result.data, isRefreshing = false) }
                is Resource.Error -> _uiState.update { it.copy(isRefreshing = false, errorMessage = result.message) }
            }
        }
    }
}
