package id.sch.santaagnes.sim.ui.admin.report

import android.os.Bundle
import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.AttendanceAdminReportDto
import id.sch.santaagnes.sim.databinding.FragmentAttendanceAdminReportBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.time.LocalDate
import id.sch.santaagnes.sim.core.DateTimeWib

/** LAP-02: Rekap kehadiran per siswa, filter kelas dan export Excel. */
@AndroidEntryPoint
class AttendanceAdminReportFragment : Fragment() {
    private var _binding: FragmentAttendanceAdminReportBinding? = null
    private val binding get() = _binding!!
    private val vm: AttendanceAdminReportViewModel by viewModels()
    private val decimal = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).apply {
        maximumFractionDigits = 2
    }
    private var optionIds: List<Int?> = listOf(null)
    private var lastOptionLabels: List<String> = emptyList()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.exportTo(uri.toString()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAttendanceAdminReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRefresh.setOnClickListener { vm.refresh() }
        binding.inputDateFrom.setOnClickListener { pickDate(vm.state.value.dateFrom) { vm.selectDateFrom(it) } }
        binding.inputDateTo.setOnClickListener { pickDate(vm.state.value.dateTo) { vm.selectDateTo(it) } }
        binding.btnClearDates.setOnClickListener { vm.clearDates() }
        binding.btnLoadMore.setOnClickListener { vm.loadMore() }
        binding.btnExport.setOnClickListener { exportLauncher.launch("laporan-kehadiran.xlsx") }
        binding.spinnerClasses.setOnItemClickListener { _, _, index, _ ->
            vm.selectClass(optionIds.getOrNull(index))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::render) }
                launch { vm.events.collect { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() } }
            }
        }
    }

    private fun render(state: AttendanceAdminReportState) {
        binding.progress.isVisible = state.loading || state.exporting
        binding.btnExport.isEnabled = !state.exporting
        binding.btnRefresh.isEnabled = !state.loading
        val from = state.dateFrom
        val to = state.dateTo
        binding.inputDateFrom.text = from?.let(DateTimeWib::formatDate) ?: "Semua tanggal"
        binding.inputDateTo.text = to?.let(DateTimeWib::formatDate) ?: "Semua tanggal"
        binding.btnClearDates.isVisible = from != null || to != null
        binding.tvPeriod.text = if (from == null && to == null) {
            "Periode: seluruh tanggal dalam semester aktif"
        } else {
            "Periode: ${from?.let(DateTimeWib::formatDate) ?: "Awal semester"} – " +
                (to?.let(DateTimeWib::formatDate) ?: "Akhir semester") +
                " (semester aktif)"
        }
        binding.btnLoadMore.isVisible = state.hasMore && !state.loading
        binding.tvError.isVisible = state.error != null
        binding.tvError.text = state.error
        binding.tvEmpty.isVisible = !state.loading && state.error == null && state.items.isEmpty()
        binding.tvTotal.text = "${state.total} baris laporan kehadiran"

        val labels = listOf("Semua kelas") + state.classOptions.map { it.name }
        if (labels != lastOptionLabels) {
            lastOptionLabels = labels
            optionIds = listOf(null) + state.classOptions.map { it.id }
            binding.spinnerClasses.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels))
        }
        val selected = optionIds.indexOf(state.classGroupId).coerceAtLeast(0)
        if (binding.spinnerClasses.text.toString() != labels[selected]) {
            binding.spinnerClasses.setText(labels[selected], false)
        }

        binding.reportRows.removeAllViews()
        state.items.forEach { addRow(it) }
    }

    private fun pickDate(selected: LocalDate?, onPicked: (LocalDate) -> Unit) {
        val initial = selected ?: DateTimeWib.today()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            onPicked(LocalDate.of(year, month + 1, day))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    private fun addRow(row: AttendanceAdminReportDto) {
        val context = requireContext()
        val card = MaterialCardView(context).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = resources.getColor(R.color.agnesian_muted, context.theme)
            setCardBackgroundColor(resources.getColor(R.color.white, context.theme))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        content.addView(line(row.studentName, 15f, true, R.color.agnesian_text))
        content.addView(line("NIS ${row.nis} • ${row.className}", 12f, false, R.color.agnesian_muted))
        content.addView(line("Hadir: ${row.present}   Sakit: ${row.sick}   Izin: ${row.excused}   Alpa: ${row.absent}", 13f, false, R.color.agnesian_text))
        val rate = row.attendanceRate?.let { "${decimal.format(it)}%" } ?: "Belum ada presensi"
        content.addView(line("Total sesi: ${row.total} • Kehadiran: $rate", 13f, true, R.color.agnesian_blue))
        card.addView(content)
        binding.reportRows.addView(card)
    }

    private fun line(s: String, size: Float, bold: Boolean, color: Int) = TextView(requireContext()).apply {
        text = s
        textSize = size
        setTextColor(resources.getColor(color, context.theme))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 0, 0, dp(5))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
