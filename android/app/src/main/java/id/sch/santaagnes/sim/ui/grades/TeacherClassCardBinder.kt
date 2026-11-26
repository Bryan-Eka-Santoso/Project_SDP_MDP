package id.sch.santaagnes.sim.ui.grades

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.domain.model.GradeAssignment
import id.sch.santaagnes.sim.domain.model.GradeSchedule

/** Pembentuk teks ringkas dan detail jadwal pada kartu kelas guru agar mudah diuji. */
internal object TeacherClassCardFormatter {

    /**
     * Ringkasan 1 baris saat kartu tertutup, mis.
     * "Semester Ganjil · 3 jadwal (Senin, Rabu)" atau "Semester Ganjil · Jadwal belum tersedia".
     */
    fun compactSummary(assignment: GradeAssignment): String {
        val semesterPrefix = assignment.semesterName.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "Semester $it" }

        val schedulePart = if (assignment.schedules.isEmpty()) {
            "Jadwal belum tersedia"
        } else {
            val days = assignment.schedules
                .map { it.day.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val countText = "${assignment.schedules.size} jadwal"
            if (days.isEmpty()) countText else "$countText (${days.joinToString(", ")})"
        }

        return listOfNotNull(semesterPrefix, schedulePart).joinToString(" · ")
    }

    /** Daftar pasangan (hari, rentang jam) untuk tampilan detail saat kartu dibuka. */
    fun detailRows(schedules: List<GradeSchedule>): List<Pair<String, String>> =
        schedules.map { slot -> slot.day.trim() to "${slot.startTime}–${slot.endTime}" }
}

/**
 * Mengisi kartu `item_grade_class` dalam mode ringkas: detail jadwal tersembunyi secara default
 * dan terbuka/tertutup saat kartu atau tombol "Jadwal" diklik.
 */
internal fun inflateTeacherClassCard(
    inflater: LayoutInflater,
    parent: ViewGroup,
    assignment: GradeAssignment,
    showNotes: Boolean = false,
    onAttendanceClick: () -> Unit,
    onGradesClick: () -> Unit,
    onNotesClick: (() -> Unit)? = null
): View {
    val card = inflater.inflate(R.layout.item_grade_class, parent, false)
    val context = card.context

    val tvAssignment = card.findViewById<TextView>(R.id.tvAssignment)
    val tvSummary = card.findViewById<TextView>(R.id.tvScheduleSummary)
    val btnToggle = card.findViewById<LinearLayout>(R.id.btnToggleSchedule)
    val tvToggle = card.findViewById<TextView>(R.id.tvToggleSchedule)
    val ivChevron = card.findViewById<ImageView>(R.id.ivScheduleChevron)
    val layoutDetail = card.findViewById<LinearLayout>(R.id.layoutScheduleDetail)
    val tvDetail = card.findViewById<TextView>(R.id.tvClassSchedule)

    tvAssignment.text = "${assignment.subjectName} · ${assignment.className}"

    val summaryText = TeacherClassCardFormatter.compactSummary(assignment)
    val blueColor = ContextCompat.getColor(context, R.color.teacher_blue)
    val inkColor = ContextCompat.getColor(context, R.color.teacher_ink)

    if (assignment.schedules.isEmpty()) {
        tvSummary.text = summaryText
        btnToggle.isVisible = false
        layoutDetail.isVisible = false
        card.isClickable = false
        card.isFocusable = false
    } else {
        // Beri highlight tebal pada jumlah jadwal & hari agar cepat terbaca tanpa membuka kartu.
        val highlightStart = summaryText.indexOf("${assignment.schedules.size} jadwal")
        if (highlightStart >= 0) {
            val sb = SpannableStringBuilder(summaryText)
            sb.setSpan(StyleSpan(Typeface.BOLD), highlightStart, summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(blueColor), highlightStart, summaryText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvSummary.text = sb
        } else {
            tvSummary.text = summaryText
        }

        val detailBuilder = SpannableStringBuilder()
        val rows = TeacherClassCardFormatter.detailRows(assignment.schedules)
        rows.forEachIndexed { idx, (day, timeRange) ->
            if (idx > 0) detailBuilder.append("\n")
            val dayStart = detailBuilder.length
            detailBuilder.append(day)
            detailBuilder.setSpan(StyleSpan(Typeface.BOLD), dayStart, detailBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            detailBuilder.setSpan(ForegroundColorSpan(inkColor), dayStart, detailBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            detailBuilder.append("  ·  ")
            val timeStart = detailBuilder.length
            detailBuilder.append(timeRange)
            detailBuilder.setSpan(StyleSpan(Typeface.BOLD), timeStart, detailBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            detailBuilder.setSpan(ForegroundColorSpan(blueColor), timeStart, detailBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tvDetail.text = detailBuilder

        var expanded = false
        fun renderExpanded(animate: Boolean) {
            layoutDetail.isVisible = expanded
            tvToggle.text = if (expanded) "Tutup" else "Jadwal"
            btnToggle.setBackgroundResource(
                if (expanded) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
            )
            val targetRotation = if (expanded) 180f else 0f
            if (animate) {
                ivChevron.animate().rotation(targetRotation).setDuration(160L).start()
            } else {
                ivChevron.rotation = targetRotation
            }
        }
        renderExpanded(animate = false)

        val toggleListener = View.OnClickListener {
            expanded = !expanded
            renderExpanded(animate = true)
        }
        card.setOnClickListener(toggleListener)
        btnToggle.setOnClickListener(toggleListener)
    }

    card.findViewById<Button>(R.id.btnAttendance).setOnClickListener { onAttendanceClick() }
    card.findViewById<Button>(R.id.btnGrades).setOnClickListener { onGradesClick() }
    card.findViewById<Button>(R.id.btnNotes).apply {
        isVisible = showNotes && onNotesClick != null
        if (onNotesClick != null) setOnClickListener { onNotesClick() }
    }

    return card
}
