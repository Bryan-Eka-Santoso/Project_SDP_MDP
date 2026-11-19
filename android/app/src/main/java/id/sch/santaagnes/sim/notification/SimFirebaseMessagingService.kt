package id.sch.santaagnes.sim.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.repository.CalendarSyncRepository
import id.sch.santaagnes.sim.domain.model.NotificationType
import id.sch.santaagnes.sim.domain.model.PushPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backend hanya mengirim data message (ADR-010), jadi [onMessageReceived] terpanggil juga saat
 * aplikasi di latar belakang; aplikasi sendiri yang menyimpan riwayat dan menampilkan notifikasi.
 */
@AndroidEntryPoint
class SimFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var handler: PushMessageHandler

    @Inject
    lateinit var registrar: PushTokenRegistrar

    @Inject
    lateinit var calendarSync: CalendarSyncRepository

    @Inject
    lateinit var session: SessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        scope.launch {
            val handled = handler.handle(message.data)
            // Peminjaman disetujui, ditolak, atau dikembalikan: perbarui kalender HP tanpa menunggu aplikasi dibuka.
            if (handled && PushPayload.from(message.data)?.type == NotificationType.BOOKING) syncCalendar()
        }
    }

    private suspend fun syncCalendar() {
        val userId = session.getUserId() ?: return
        val role = session.getRole() ?: return
        calendarSync.syncIfEnabled(userId, role, force = true)
    }

    override fun onNewToken(token: String) {
        scope.launch { registrar.register(token) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
