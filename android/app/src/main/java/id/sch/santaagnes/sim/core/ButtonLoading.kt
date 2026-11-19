package id.sch.santaagnes.sim.core

import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable

/**
 * Tampilkan animasi loading di dalam tombol (di kiri teks), bukan spinner terpisah di layar.
 * Tombol tetap berwarna normal; klik diabaikan selama loading supaya aksi tidak terkirim dua kali.
 * Tag `<Button>` di tema Material otomatis menjadi MaterialButton, jadi cast di bawah aman.
 */
fun Button.setLoading(loading: Boolean) {
    val button = this as? MaterialButton ?: return
    button.isClickable = !loading
    if (!loading) {
        button.icon = null
        return
    }
    if (button.icon is IndeterminateDrawable<*>) return // sudah berputar; jangan diulang tiap state baru
    val spec = CircularProgressIndicatorSpec(
        button.context,
        null,
        0,
        com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
    ).apply {
        indicatorColors = intArrayOf(button.currentTextColor)
    }
    button.icon = IndeterminateDrawable.createCircularDrawable(button.context, spec)
    button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
    button.iconTint = null
}
