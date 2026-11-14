package id.sch.santaagnes.sim.notification

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Pembungkus token FCM perangkat agar pemanggilnya bisa diuji tanpa Firebase. */
interface PushTokenProvider {

    /** Token FCM saat ini, atau null bila tidak bisa didapat (mis. offline, Play Services tidak ada). */
    suspend fun currentToken(): String?

    /** Mencabut token di perangkat; token baru dibuat saat dipakai lagi. Kegagalan diabaikan. */
    suspend fun deleteToken()
}

@Singleton
class FirebasePushTokenProvider @Inject constructor() : PushTokenProvider {

    override suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (continuation.isActive) continuation.resume(task.result?.takeIf { task.isSuccessful })
            }
        } catch (_: Exception) {
            // Firebase belum terinisialisasi atau Play Services tidak tersedia.
            if (continuation.isActive) continuation.resume(null)
        }
    }

    override suspend fun deleteToken() = suspendCancellableCoroutine { continuation ->
        try {
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener {
                if (continuation.isActive) continuation.resume(Unit)
            }
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}
