package id.sch.santaagnes.sim.notification

import id.sch.santaagnes.sim.data.repository.NotificationRepository
import id.sch.santaagnes.sim.domain.model.PushPayload
import javax.inject.Inject
import javax.inject.Singleton

/** Menangani satu data message FCM: simpan ke riwayat, lalu tampilkan di sistem. */
@Singleton
class PushMessageHandler @Inject constructor(
    private val repository: NotificationRepository,
    private val presenter: NotificationPresenter
) {

    /** True bila pesan diproses; false bila bukan notifikasi aplikasi atau tidak ada akun login. */
    suspend fun handle(data: Map<String, String>): Boolean {
        val payload = PushPayload.from(data) ?: return false
        val saved = repository.save(payload) ?: return false
        presenter.show(saved)
        return true
    }
}
