package id.sch.santaagnes.sim.ui.progress

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.ProgressNoteDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// teacher = judul kartu memakai nama siswa; hanya kartu dengan click yang bisa diubah.
internal fun addProgressNoteCard(container: LinearLayout, note: ProgressNoteDto, teacher: Boolean, click: (() -> Unit)? = null) {
    val context = container.context
    val density = context.resources.displayMetrics.density
    fun dp(n: Int) = (n * density).toInt()
    val card = MaterialCardView(context).apply {
        radius = dp(14).toFloat()
        cardElevation = dp(1).toFloat()
        setCardBackgroundColor(context.getColor(R.color.white))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        if (click != null) { isClickable = true; isFocusable = true; setOnClickListener { click() } }
    }
    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(16))
    }
    fun line(s: String, bold: Boolean = false, small: Boolean = false) {
        column.addView(TextView(context).apply {
            text = s; textSize = if (small) 12f else 15f
            setTextColor(context.getColor(if (small) R.color.agnesian_muted else R.color.agnesian_text))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(7))
        })
    }
    line(if (teacher) note.studentName ?: "Siswa #${note.studentId}" else note.subjectName ?: "Catatan Guru", true)
    if (teacher) line(note.nis?.let { "NIS $it" } ?: "", small = true)
    else line(note.className.orEmpty(), small = true)
    val readable = runCatching { LocalDate.parse(note.date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))) }.getOrDefault(note.date)
    line(readable, small = true)
    line(note.content)
    if (click != null) line("Ketuk untuk mengedit", small = true)
    card.addView(column)
    container.addView(card)
}
