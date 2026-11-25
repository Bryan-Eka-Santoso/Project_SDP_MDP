package id.sch.santaagnes.sim.ui.common

import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import id.sch.santaagnes.sim.R

/**
 * Standar highlight chip filter aktif/nonaktif agar status pilihan selalu jelas di semua role.
 * [onDarkHeader] = true untuk chip di atas header biru (aktif = putih teks biru; nonaktif = transparan teks putih).
 */
fun Chip.applyAgnesianFilterStyle(onDarkHeader: Boolean = false) {
    val ctx = context
    isCheckable = true
    isCheckedIconVisible = true
    setTypeface(typeface, Typeface.BOLD)
    chipStrokeWidth = ctx.resources.displayMetrics.density
    if (onDarkHeader) {
        chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.chip_header_bg)
        setTextColor(ContextCompat.getColorStateList(ctx, R.color.chip_header_text))
        chipStrokeColor = ContextCompat.getColorStateList(ctx, R.color.chip_header_stroke)
        checkedIconTint = ContextCompat.getColorStateList(ctx, R.color.agnesian_blue)
    } else {
        chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.chip_filter_bg)
        setTextColor(ContextCompat.getColorStateList(ctx, R.color.chip_filter_text))
        chipStrokeColor = ContextCompat.getColorStateList(ctx, R.color.chip_filter_stroke)
        checkedIconTint = ContextCompat.getColorStateList(ctx, R.color.white)
    }
}
