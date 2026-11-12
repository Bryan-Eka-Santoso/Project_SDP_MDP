package id.sch.santaagnes.sim.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sch.santaagnes.sim.MainActivity
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.domain.model.AppNotification
import javax.inject.Inject
import javax.inject.Singleton

/** Menampilkan notifikasi sistem untuk pesan yang masuk. */
interface NotificationPresenter {
    fun show(notification: AppNotification)
}

@Singleton
class SystemNotificationPresenter @Inject constructor(
    @param:ApplicationContext private val context: Context
) : NotificationPresenter {

    @SuppressLint("MissingPermission") // izin diperiksa di canPost()
    override fun show(notification: AppNotification) {
        if (!canPost()) return

        // Mengetuk notifikasi membuka aplikasi; tab Notifikasi menampilkan riwayatnya.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notification.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channel = NotificationChannelSpec.forType(notification.type)
        val built = NotificationCompat.Builder(context, channel.channelId)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notification.id.toInt(), built)
    }

    // Android 13+ menolak notifikasi tanpa izin POST_NOTIFICATIONS; riwayat di Room tetap tersimpan.
    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
