package id.sch.santaagnes.sim.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.domain.model.NotificationType

/** Channel notifikasi per jenis (jadwal, pengumuman, akademik, peminjaman, pembayaran). */
enum class NotificationChannelSpec(val channelId: String, @param:StringRes val nameRes: Int) {
    SCHEDULE("schedule", R.string.channel_schedule),
    ANNOUNCEMENT("announcement", R.string.channel_announcement),
    ACADEMIC("academic", R.string.channel_academic),
    BOOKING("booking", R.string.channel_booking),
    BILL("bill", R.string.channel_bill),
    GENERAL("general", R.string.channel_general);

    companion object {
        fun forType(type: NotificationType): NotificationChannelSpec = when (type) {
            NotificationType.SCHEDULE_CHANGE, NotificationType.TASK -> SCHEDULE
            NotificationType.ANNOUNCEMENT -> ANNOUNCEMENT
            NotificationType.VIOLATION, NotificationType.LEAVE_REQUEST -> ACADEMIC
            NotificationType.BOOKING -> BOOKING
            NotificationType.BILL_OVERDUE -> BILL
            NotificationType.OTHER -> GENERAL
        }

        /** Membuat semua channel; aman dipanggil berulang. Sebelum Android 8 tidak ada channel. */
        fun createAll(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            entries.forEach { spec ->
                manager.createNotificationChannel(
                    NotificationChannel(spec.channelId, context.getString(spec.nameRes), NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }
}
