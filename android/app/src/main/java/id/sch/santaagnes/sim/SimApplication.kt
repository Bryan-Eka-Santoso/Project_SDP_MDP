package id.sch.santaagnes.sim

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import id.sch.santaagnes.sim.notification.NotificationChannelSpec

@HiltAndroidApp
class SimApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Desain Figma hanya terang; layar memakai warna tetap (kartu putih), jadi tema gelap dimatikan.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        NotificationChannelSpec.createAll(this)
    }
}
