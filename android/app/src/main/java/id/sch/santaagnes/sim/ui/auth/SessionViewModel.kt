package id.sch.santaagnes.sim.ui.auth

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Aksi sesi yang dipakai banyak layar (saat ini: keluar). */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val loggedOutEvents = Channel<Unit>(Channel.BUFFERED)
    val loggedOut = loggedOutEvents.receiveAsFlow()

    private var isLoggingOut = false

    fun logout() {
        if (isLoggingOut) return
        isLoggingOut = true
        viewModelScope.launch {
            repository.logout()
            loggedOutEvents.send(Unit)
            isLoggingOut = false
        }
    }
}

/** Pasang di onViewCreated: setelah keluar selesai, pindah ke layar login. */
fun Fragment.observeLogout(viewModel: SessionViewModel) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.loggedOut.collect { findNavController().navigateToLogin() }
        }
    }
}
