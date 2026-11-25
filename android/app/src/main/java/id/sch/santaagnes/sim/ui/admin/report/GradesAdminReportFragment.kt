package id.sch.santaagnes.sim.ui.admin.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import id.sch.santaagnes.sim.data.remote.GradesAdminReportDto
import id.sch.santaagnes.sim.databinding.FragmentGradesAdminReportBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** LAP-01: Laporan nilai siswa per mapel, lengkap dengan filter kelas dan export Excel. */
@AndroidEntryPoint
class GradesAdminReportFragment : Fragment() {
    private var _binding: FragmentGradesAdminReportBinding? = null
    private val binding get() = _binding!!
    private val vm: GradesAdminReportViewModel by viewModels()
    private val decimal = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).apply {
        maximumFractionDigits = 2
    }
    private var optionIds: List<Int?> = listOf(null)
    private var lastOptionLabels: List<String> = emptyList()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.exportTo(uri.toString()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGradesAdminReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRefresh.setOnClickListener { vm.refresh() }
        binding.btnLoadMore.setOnClickListener { vm.loadMore() }
        binding.btnExport.setOnClickListener { exportLauncher.launch("laporan-nilai.xlsx") }
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

    private fun render(state: GradesAdminReportState) {
        binding.progress.isVisible = state.loading || state.exporting
        binding.btnExport.isEnabled = !state.exporting
        binding.btnRefresh.isEnabled = !state.loading
        binding.btnLoadMore.isVisible = state.hasMore && !state.loading
        binding.tvError.isVisible = state.error != null
        binding.tvError.text = state.error
        binding.tvEmpty.isVisible = !state.loading && state.error == null && state.items.isEmpty()
        binding.tvTotal.text = "${state.total} baris laporan nilai"

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
        if (state.items.isNotEmpty()) {
            binding.reportRows.addView(buildGradesChart(state.items))
        }
        state.items.forEach { addRow(it) }
    }

    private fun buildGradesChart(items: List<GradesAdminReportDto>): View {
        val ctx = requireContext()
        val scored = items.mapNotNull { it.finalScore }
        val avg = if (scored.isNotEmpty()) decimal.format(scored.average()) else "-"
        val completeCount = items.count { it.complete }
        val cGreen = ContextCompat.getColor(ctx, R.color.bill_paid)
        val cBlue = ContextCompat.getColor(ctx, R.color.agnesian_blue)
        val cAmber = ContextCompat.getColor(ctx, R.color.change_substitute)
        val cRed = ContextCompat.getColor(ctx, R.color.change_cancel)
        val cMuted = ContextCompat.getColor(ctx, R.color.agnesian_muted)

        val grouped = items.groupBy { it.predicate?.trim()?.uppercase()?.ifEmpty { null } ?: "Belum Lengkap" }
        val orderedKeys = listOf("A", "B", "C", "D", "E", "Belum Lengkap")
            .filter { grouped.containsKey(it) } + grouped.keys.filter { it !in setOf("A", "B", "C", "D", "E", "Belum Lengkap") }
        val segments = orderedKeys.map { key ->
            val count = grouped[key]?.size ?: 0
            val color = when (key) {
                "A" -> cGreen
                "B" -> cBlue
                "C" -> cAmber
                "Belum Lengkap" -> cMuted
                else -> cRed
            }
            ChartSegment(
                label = if (key == "Belum Lengkap") key else "Predikat $key",
                value = count.toFloat(),
                displayValue = "$count",
                color = color
            )
        }
        return ReportChartRenderer.buildCard(
            ctx,
            ReportChartSpec(
                title = "Distribusi Predikat Nilai",
                subtitle = "Berdasarkan ${items.size} baris nilai yang ditampilkan",
                kind = ReportChartKind.DONUT,
                centerLabel = avg,
                centerSubLabel = "Rata-rata",
                kpis = listOf(
                    ChartKpi("Nilai Lengkap", "$completeCount / ${items.size}", cGreen),
                    ChartKpi("Rata-rata Nilai", avg, cBlue)
                ),
                segments = segments
            )
        )
    }

    private fun addRow(row: GradesAdminReportDto) {
        val context = requireContext()
        val card = MaterialCardView(context).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(context, R.color.agnesian_border)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titleCol.addView(line(row.studentName, 15f, true, R.color.agnesian_text))
        titleCol.addView(line("NIS ${row.nis} • ${row.className}", 12f, false, R.color.agnesian_muted))
        header.addView(titleCol)
        val badgeText = row.predicate?.let { "Predikat $it" } ?: "Belum Lengkap"
        val badgeColor = when (row.predicate?.uppercase()) {
            "A" -> R.color.bill_paid
            "B" -> R.color.agnesian_blue
            "C" -> R.color.change_substitute
            null -> R.color.agnesian_muted
            else -> R.color.change_cancel
        }
        header.addView(ReportChartRenderer.buildBadge(context, badgeText, badgeColor))
        content.addView(header)

        content.addView(line(row.subjectName, 13f, true, R.color.agnesian_blue))
        val score = row.finalScore?.let { decimal.format(it) } ?: "Belum lengkap"
        val scoreColor = when {
            row.finalScore == null -> R.color.change_substitute
            row.finalScore >= 75.0 -> R.color.bill_paid
            else -> R.color.change_cancel
        }
        content.addView(line("Nilai Akhir: $score", 14f, true, scoreColor))
        content.addView(
            line(
                if (row.complete) "Komponen nilai lengkap" else "Komponen nilai belum lengkap",
                12f,
                true,
                if (row.complete) R.color.bill_paid else R.color.change_substitute
            )
        )
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
