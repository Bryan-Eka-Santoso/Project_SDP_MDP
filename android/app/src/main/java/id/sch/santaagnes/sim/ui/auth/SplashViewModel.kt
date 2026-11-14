package id.sch.santaagnes.sim.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.local.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashDestination {
    data object Login : SplashDestination
    data class Home(val role: String) : SplashDestination
}

/** Menentukan layar pertama: langsung ke beranda role bila sesi tersimpan, selain itu login. */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val role = sessionManager.getRole()
            _destination.value = if (sessionManager.hasSession() && role != null) {
                SplashDestination.Home(role)
            } else {
                SplashDestination.Login
            }
        }
    }
}
