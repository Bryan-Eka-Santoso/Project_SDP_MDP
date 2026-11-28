package id.sch.santaagnes.sim.ui.admin.report

import id.sch.santaagnes.sim.core.setLoading
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
import id.sch.santaagnes.sim.data.remote.TeachingJournalReportDto
import id.sch.santaagnes.sim.databinding.FragmentTeachingJournalReportBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import id.sch.santaagnes.sim.core.DateTimeWib

/** LAP-06: Laporan jurnal per sesi, filter kelas/guru/periode dan export Excel. */
@AndroidEntryPoint
class TeachingJournalReportFragment : Fragment() {
    private var _binding: FragmentTeachingJournalReportBinding? = null
    private val binding get() = _binding!!
    private val vm: TeachingJournalReportViewModel by viewModels()
    private var optionIds: List<Int?> = listOf(null)
    private var lastOptionLabels: List<String> = emptyList()
    private var teacherIds: List<Int?> = listOf(null)
    private var lastTeacherLabels: List<String> = emptyList()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.exportTo(uri.toString()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeachingJournalReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRefresh.setOnClickListener { vm.refresh() }
        binding.inputDateFrom.setOnClickListener { pickDate(vm.state.value.dateFrom) { vm.selectDateFrom(it) } }
        binding.inputDateTo.setOnClickListener { pickDate(vm.state.value.dateTo) { vm.selectDateTo(it) } }
        binding.btnClearDates.setOnClickListener { vm.clearDates() }
        binding.btnLoadMore.setOnClickListener { vm.loadMore() }
        binding.btnExport.setOnClickListener {
            val state = vm.state.value
            val fileName = "laporan-teaching-journals-${DateTimeWib.today()}.xlsx"
            val filters = buildList {
                state.classGroupId?.let { id ->
                    state.classOptions.firstOrNull { it.id == id }?.name?.let { add("Kelas: $it") }
                }
                state.teacherId?.let { id ->
                    state.teacherOptions.firstOrNull { it.id == id }?.name?.let { add("Guru: $it") }
                }
                if (state.dateFrom != null || state.dateTo != null) {
                    add(
                        "Periode: ${state.dateFrom?.let(DateTimeWib::formatDate) ?: "..."} – " +
                            (state.dateTo?.let(DateTimeWib::formatDate) ?: "...")
                    )
                }
            }
            showReportExportPreview(
                ReportExportPreviewSpec(
                    reportTitle = "Laporan Jurnal Mengajar",
                    fileName = fileName,
                    totalCount = state.total,
                    filters = filters,
                    columns = listOf("Tanggal", "Kelas", "Mapel", "Guru", "Catatan Jurnal"),
                    rows = state.items.map { row ->
                        val dateText = runCatching { DateTimeWib.formatDate(LocalDate.parse(row.date)) }
                            .getOrDefault(row.date)
                        listOf(
                            dateText,
                            row.className,
                            row.subjectName,
                            row.teacherName,
                            row.journal?.takeIf { it.isNotBlank() } ?: "Belum diisi"
                        )
                    }
                )
            ) {
                exportLauncher.launch(fileName)
            }
        }
        binding.spinnerClasses.setOnItemClickListener { _, _, index, _ ->
            vm.selectClass(optionIds.getOrNull(index))
        }
        binding.spinnerTeachers.setOnItemClickListener { _, _, index, _ ->
            vm.selectTeacher(teacherIds.getOrNull(index))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::render) }
                launch { vm.events.collect { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() } }
            }
        }
    }

    private fun render(state: TeachingJournalReportState) {
        binding.progress.isVisible = state.loading
        binding.btnExport.setLoading(state.exporting)
        binding.btnRefresh.isEnabled = !state.loading
        val from = state.dateFrom
        val to = state.dateTo
        binding.inputDateFrom.text = from?.let(DateTimeWib::formatDate) ?: "Semua tanggal"
        binding.inputDateTo.text = to?.let(DateTimeWib::formatDate) ?: "Semua tanggal"
        binding.btnClearDates.isVisible = from != null || to != null
        binding.tvPeriod.text = if (from == null && to == null) {
            "Periode: seluruh sesi pada semester aktif"
        } else {
            "Periode: ${from?.let(DateTimeWib::formatDate) ?: "Awal semester"} – " +
                (to?.let(DateTimeWib::formatDate) ?: "Akhir semester") +
                " (semester aktif)"
        }
        binding.btnLoadMore.isVisible = state.hasMore && !state.loading
        binding.tvError.isVisible = state.error != null
        binding.tvError.text = state.error
        binding.tvEmpty.isVisible = !state.loading && state.error == null && state.items.isEmpty()
        binding.tvTotal.text = "${state.total} sesi jurnal mengajar"

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

        val teachers = listOf("Semua guru") + state.teacherOptions.map { it.name }
        if (teachers != lastTeacherLabels) {
            lastTeacherLabels = teachers
            teacherIds = listOf(null) + state.teacherOptions.map { it.id }
            binding.spinnerTeachers.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, teachers))
        }
        val teacherIndex = teacherIds.indexOf(state.teacherId).coerceAtLeast(0)
        if (binding.spinnerTeachers.text.toString() != teachers[teacherIndex]) {
            binding.spinnerTeachers.setText(teachers[teacherIndex], false)
        }

        binding.reportRows.removeAllViews()
        if (state.items.isNotEmpty()) {
            binding.reportRows.addView(buildJournalChart(state.items))
        }
        state.items.forEach { addRow(it) }
    }

    private fun buildJournalChart(items: List<TeachingJournalReportDto>): View {
        val ctx = requireContext()
        val filled = items.count { !it.journal.isNullOrBlank() }
        val empty = items.size - filled
        val cBlue = ContextCompat.getColor(ctx, R.color.agnesian_blue)
        val cGreen = ContextCompat.getColor(ctx, R.color.bill_paid)
        val cAmber = ContextCompat.getColor(ctx, R.color.change_substitute)
        val byClass = items.groupingBy { it.className }.eachCount()
            .entries.sortedByDescending { it.value }.take(6)
        return ReportChartRenderer.buildCard(
            ctx,
            ReportChartSpec(
                title = "Jumlah Sesi Jurnal per Kelas",
                subtitle = "Berdasarkan ${items.size} sesi yang ditampilkan",
                kind = ReportChartKind.HORIZONTAL_BAR,
                kpis = listOf(
                    ChartKpi("Jurnal Terisi", "$filled sesi", cGreen),
                    ChartKpi("Belum Diisi", "$empty sesi", if (empty > 0) cAmber else cGreen)
                ),
                segments = byClass.map { (cls, count) ->
                    ChartSegment(cls, count.toFloat(), "$count sesi", cBlue)
                }
            )
        )
    }

    private fun pickDate(selected: LocalDate?, onPicked: (LocalDate) -> Unit) {
        val initial = selected ?: DateTimeWib.today()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            onPicked(LocalDate.of(year, month + 1, day))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    private fun addRow(row: TeachingJournalReportDto) {
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
        val dateText = runCatching { DateTimeWib.formatDate(LocalDate.parse(row.date)) }
            .getOrDefault(row.date)
        val hasJournal = !row.journal.isNullOrBlank()
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titleCol.addView(line(row.subjectName, 15f, true, R.color.agnesian_text))
        titleCol.addView(line("${row.className} • ${row.teacherName}", 12f, true, R.color.agnesian_muted))
        header.addView(titleCol)
        header.addView(
            ReportChartRenderer.buildBadge(
                context,
                if (hasJournal) "Terisi" else "Kosong",
                if (hasJournal) R.color.bill_paid else R.color.change_substitute
            )
        )
        content.addView(header)
        content.addView(line(dateText, 13f, true, R.color.agnesian_blue))
        content.addView(
            line(
                row.journal?.takeIf { it.isNotBlank() } ?: "Belum ada catatan jurnal.",
                13f,
                !hasJournal,
                if (hasJournal) R.color.agnesian_text else R.color.change_substitute
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
