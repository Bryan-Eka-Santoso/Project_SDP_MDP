package id.sch.santaagnes.sim.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.repository.NotificationRepository
import id.sch.santaagnes.sim.domain.model.AppNotification
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationListUiState(
    // null selama riwayat pertama kali dimuat dari Room
    val items: List<AppNotification>? = null
) {
    val isLoading: Boolean get() = items == null
    val isEmpty: Boolean get() = items?.isEmpty() == true
    val unreadCount: Int get() = items.orEmpty().count { !it.isRead }
}

@HiltViewModel
class NotificationListViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationListUiState> = repository.observe()
        .map { NotificationListUiState(items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotificationListUiState())

    fun markRead(id: Long) {
        viewModelScope.launch { repository.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
