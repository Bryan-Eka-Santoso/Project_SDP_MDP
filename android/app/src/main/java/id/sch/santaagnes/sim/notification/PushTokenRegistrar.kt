package id.sch.santaagnes.sim.notification

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.DeviceTokenApi
import id.sch.santaagnes.sim.data.remote.DeviceTokenRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mengirim token FCM ke backend supaya akun ini bisa dikirimi push. Dipanggil setelah login, saat
 * aplikasi dibuka dengan sesi tersimpan, dan saat Firebase menerbitkan token baru.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val provider: PushTokenProvider,
    private val api: DeviceTokenApi,
    private val session: SessionManager,
    private val errors: ApiErrorMapper
) {

    /** Mendaftarkan token saat ini. Gagal tidak mengganggu pengguna; dicoba lagi saat aplikasi dibuka. */
    suspend fun register(): Resource<Unit> {
        val token = provider.currentToken() ?: return Resource.Error("Token notifikasi belum tersedia.")
        return register(token)
    }

    suspend fun register(token: String): Resource<Unit> {
        // Tanpa sesi, token baru akan dikirim saat login berikutnya.
        if (!session.hasSession()) return Resource.Error("Belum login.")
        return errors.call { api.register(DeviceTokenRequest(token)) }.let { result ->
            if (result is Resource.Error) result else Resource.Success(Unit)
        }
    }
}
