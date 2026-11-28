package id.sch.santaagnes.sim.ui.attendance

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.AttendanceSummaryEntryDto
import id.sch.santaagnes.sim.databinding.FragmentStudentAttendanceSummaryBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@AndroidEntryPoint
class StudentAttendanceSummaryFragment : Fragment() {
    private var _binding: FragmentStudentAttendanceSummaryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StudentAttendanceSummaryViewModel by viewModels()
    private var selectedDate: LocalDate? = null
    private var lastMonth: YearMonth? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStudentAttendanceSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnAttendanceBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnPreviousMonth.setOnClickListener { viewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }
        binding.btnAttendanceRetry.setOnClickListener { viewModel.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: StudentAttendanceSummaryState) {
        if (lastMonth != state.month) {
            lastMonth = state.month
            selectedDate = null
        }
        binding.summaryProgress.isVisible = state.loading
        state.childName?.let { binding.tvAttendanceSubtitle.text = getString(R.string.attendance_child_subtitle, it) }
        binding.tvAttendanceError.isVisible = state.error != null
        binding.btnAttendanceRetry.isVisible = state.error != null
        binding.tvAttendanceError.text = state.error.orEmpty()
        binding.tvAttendanceMonth.text = state.month.format(
            java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID"))
        )
        val data = state.summary
        binding.tvAttendanceStats.text = when {
            state.loading -> "Memuat rekap..."
            data == null -> "Belum dapat menampilkan rekap."
            data.total == 0 -> "Belum ada presensi yang dicatat pada bulan ini."
            else -> buildStatsSpannable(
                present = data.counts.PRESENT,
                sick = data.counts.SICK,
                excused = data.counts.EXCUSED,
                absent = data.counts.ABSENT,
                total = data.total,
                percentage = data.attendancePercentage
            )
        }
        // Satu tanggal bisa memiliki beberapa sesi; semua detail tetap ditampilkan.
        val entries = data?.entries.orEmpty()
        renderCalendar(state.month, entries)
        renderSelectedDay(entries)
        val spannable = android.text.SpannableString("● Hadir     ● Sakit/Izin     ● Alpa")
        val dotPositions = spannable.indices.filter { spannable[it] == '●' }
        listOf(BLUE, ORANGE, RED).forEachIndexed { index, color ->
            dotPositions.getOrNull(index)?.let { pos ->
                spannable.setSpan(android.text.style.ForegroundColorSpan(color), pos, pos + 1, 0)
            }
        }
        binding.tvAttendanceLegend.text = spannable
    }

    private fun buildStatsSpannable(
        present: Int,
        sick: Int,
        excused: Int,
        absent: Int,
        total: Int,
        percentage: Double?
    ): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        fun appendStat(label: String, value: String, color: Int) {
            sb.append(label)
            val start = sb.length
            sb.append(value)
            sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, sb.length, 0)
            sb.setSpan(android.text.style.ForegroundColorSpan(color), start, sb.length, 0)
        }
        appendStat("Hadir: ", present.toString(), BLUE)
        sb.append("    ")
        appendStat("Sakit: ", sick.toString(), ORANGE)
        sb.append("\n")
        appendStat("Izin: ", excused.toString(), ORANGE)
        sb.append("    ")
        appendStat("Alpa: ", absent.toString(), RED)
        sb.append("\n")
        appendStat("Total sesi: ", total.toString(), requireContext().getColor(R.color.agnesian_text))
        sb.append("    ")
        val pctColor = when {
            percentage == null -> MUTED
            percentage >= 85.0 -> GREEN
            percentage >= 70.0 -> ORANGE
            else -> RED
        }
        val pctLabel = percentage?.let {
            if (it % 1.0 == 0.0) "${it.toInt()}%" else String.format(Locale("id", "ID"), "%.1f%%", it)
        } ?: "—"
        appendStat("Kehadiran: ", pctLabel, pctColor)
        return sb
    }

    private fun renderCalendar(month: YearMonth, entries: List<AttendanceSummaryEntryDto>) {
        val grid = binding.attendanceCalendar
        grid.removeAllViews()
        val headers = listOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN")
        headers.forEach { addCell(grid, it, MUTED, bold = true, onClick = null) }
        val offset = month.atDay(1).dayOfWeek.value - 1 // Senin = 0
        repeat(offset) { addCell(grid, "", MUTED, onClick = null) }
        val byDay = entries.groupBy { it.date }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val statuses = byDay[date.toString()].orEmpty().map { it.status }
            // Prioritas warna apabila status suatu hari berbeda per mata pelajaran.
            val color = when {
                "ABSENT" in statuses -> RED
                "SICK" in statuses || "EXCUSED" in statuses -> ORANGE
                "PRESENT" in statuses -> BLUE
                else -> MUTED
            }
            addCell(grid, day.toString(), color, date == selectedDate) {
                selectedDate = date
                renderCalendar(month, entries)
                renderSelectedDay(entries)
            }
        }
    }

    private fun addCell(grid: GridLayout, label: String, textColor: Int, bold: Boolean = false, onClick: (() -> Unit)?) {
        val size = resources.displayMetrics.density
        val cell = TextView(requireContext()).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = if (onClick == null && bold) 10f else 13f
            val selected = onClick != null && selectedDate?.dayOfMonth?.toString() == label
            setTextColor(if (selected) Color.WHITE else textColor)
            if (bold || onClick != null) setTypeface(typeface, Typeface.BOLD)
            if (selected) {
                setBackgroundResource(R.drawable.bg_badge)
                backgroundTintList = ColorStateList.valueOf(BLUE)
            }
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
                contentDescription = "Tanggal $label, pilih untuk melihat presensi"
            }
        }
        grid.addView(cell, GridLayout.LayoutParams().apply {
            width = 0
            height = (43 * size).toInt()
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        })
    }

    private fun renderSelectedDay(entries: List<AttendanceSummaryEntryDto>) {
        val date = selectedDate
        binding.tvAttendanceDayTitle.text = if (date == null) "Detail Presensi" else "Presensi ${date.dayOfMonth} ${lastMonth?.month?.getDisplayName(java.time.format.TextStyle.FULL, Locale("id", "ID"))}"
        val details = if (date == null) emptyList() else entries.filter { it.date == date.toString() }
        binding.tvAttendanceDayDetails.text = when {
            date == null -> "Pilih tanggal pada kalender untuk melihat detail presensi."
            details.isEmpty() -> "Belum ada catatan presensi untuk tanggal ini."
            else -> {
                val sb = android.text.SpannableStringBuilder()
                val textDark = requireContext().getColor(R.color.agnesian_text)
                details.forEachIndexed { index, entry ->
                    if (index > 0) sb.append("\n\n")
                    val subjStart = sb.length
                    sb.append(entry.subjectName)
                    sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), subjStart, sb.length, 0)
                    sb.setSpan(android.text.style.ForegroundColorSpan(textDark), subjStart, sb.length, 0)
                    sb.append("  ·  ")
                    val statusStart = sb.length
                    sb.append(label(entry.status))
                    sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), statusStart, sb.length, 0)
                    val statusColor = when (entry.status) {
                        "PRESENT" -> GREEN
                        "SICK", "EXCUSED" -> ORANGE
                        "ABSENT" -> RED
                        else -> MUTED
                    }
                    sb.setSpan(android.text.style.ForegroundColorSpan(statusColor), statusStart, sb.length, 0)
                }
                sb
            }
        }
    }

    private fun label(status: String): String = when (status) {
        "PRESENT" -> "Hadir"
        "SICK" -> "Sakit"
        "EXCUSED" -> "Izin"
        "ABSENT" -> "Alpa"
        else -> status
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val BLUE = Color.rgb(30, 80, 202)
        val GREEN = Color.rgb(46, 125, 50)
        val ORANGE = Color.rgb(219, 152, 26)
        val RED = Color.rgb(214, 51, 61)
        val MUTED = Color.rgb(148, 154, 165)
    }
}
