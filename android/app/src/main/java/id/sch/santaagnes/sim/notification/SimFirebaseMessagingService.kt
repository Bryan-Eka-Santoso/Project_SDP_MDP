package id.sch.santaagnes.sim.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        scope.launch { handler.handle(message.data) }
    }

    override fun onNewToken(token: String) {
        scope.launch { registrar.register(token) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
