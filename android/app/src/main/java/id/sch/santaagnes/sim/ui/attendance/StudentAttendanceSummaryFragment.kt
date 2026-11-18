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
            else -> "Masuk: ${data.counts.PRESENT}    Sakit: ${data.counts.SICK}\n" +
                "Izin: ${data.counts.EXCUSED}    Bolos: ${data.counts.ABSENT}\n" +
                "Total sesi: ${data.total}    Kehadiran: ${data.attendancePercentage?.let { "$it%" } ?: "—"}"
        }
        // Satu tanggal bisa memiliki beberapa sesi; semua detail tetap ditampilkan.
        val entries = data?.entries.orEmpty()
        renderCalendar(state.month, entries)
        renderSelectedDay(entries)
        val spannable = android.text.SpannableString("● Masuk     ● Sakit/Izin     ● Bolos")
        val dotPositions = spannable.indices.filter { spannable[it] == '●' }
        listOf(BLUE, ORANGE, RED).forEachIndexed { index, color ->
            dotPositions.getOrNull(index)?.let { pos ->
                spannable.setSpan(android.text.style.ForegroundColorSpan(color), pos, pos + 1, 0)
            }
        }
        binding.tvAttendanceLegend.text = spannable
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
            textSize = if (bold) 10f else 13f
            setTextColor(textColor)
            if (bold || onClick != null) setTypeface(typeface, Typeface.BOLD)
            val selected = onClick != null && selectedDate?.dayOfMonth?.toString() == label
            if (selected) {
                setBackgroundResource(R.drawable.bg_badge)
                backgroundTintList = ColorStateList.valueOf(0xFFEAF0FF.toInt())
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
            else -> details.joinToString("\n\n") { "${it.subjectName} · ${label(it.status)}" }
        }
    }

    private fun label(status: String): String = when (status) {
        "PRESENT" -> "Masuk"
        "SICK" -> "Sakit"
        "EXCUSED" -> "Izin"
        "ABSENT" -> "Bolos"
        else -> status
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val BLUE = Color.rgb(46, 100, 214)
        val ORANGE = Color.rgb(219, 152, 26)
        val RED = Color.rgb(214, 51, 61)
        val MUTED = Color.rgb(148, 154, 165)
    }
}
