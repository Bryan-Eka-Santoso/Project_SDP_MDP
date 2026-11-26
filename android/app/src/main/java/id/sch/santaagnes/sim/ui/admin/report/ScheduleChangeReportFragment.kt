package id.sch.santaagnes.sim.ui.admin.report

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportFilter
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportRow

/** Laporan perubahan jadwal (F-LAP-07): filter kelas, guru (asli/pengganti), jenis, dan rentang tanggal. */
@AndroidEntryPoint
class ScheduleChangeReportFragment : ReportFragment<ScheduleChangeReportRow, ScheduleChangeReportFilter>() {

    override val viewModel: ReportViewModel<ScheduleChangeReportRow, ScheduleChangeReportFilter> by
        viewModels<ScheduleChangeReportViewModel>()

    override val adapter = ScheduleChangeReportAdapter()
    override val titleRes = R.string.report_schedule_changes_title
    override val reportType = "schedule-changes"
    override val totalRes = R.string.report_total_changes

    private val scheduleViewModel get() = viewModel as ScheduleChangeReportViewModel

    // Jenis yang tampil sebagai chip: null = semua, lalu tiga jenis perubahan.
    private val types: List<ChangeType?> = listOf(null) + ChangeType.entries

    private var classDropdown: ReportDropdown? = null
    private var teacherDropdown: ReportDropdown? = null

    override fun setupFilters() {
        classDropdown = ReportDropdown(binding.inputClass) { scheduleViewModel.selectClassGroup(it) }
        teacherDropdown = ReportDropdown(binding.inputTeacher) { scheduleViewModel.selectTeacher(it) }

        binding.scrollTypes.isVisible = true
        binding.chipTypes.removeAllViews()
        types.forEach { type ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(
                    when (type) {
                        null -> R.string.admin_filter_all_types
                        ChangeType.SUBSTITUTE -> R.string.schedule_change_substitute
                        ChangeType.RESCHEDULE -> R.string.schedule_change_reschedule
                        ChangeType.CANCEL -> R.string.schedule_change_cancel
                    }
                )
                isCheckable = true
                setOnClickListener { scheduleViewModel.selectType(type) }
            }
            binding.chipTypes.addView(chip)
        }

        setupDateRange(
            currentFrom = { scheduleViewModel.uiState.value.filter.dateFrom },
            currentTo = { scheduleViewModel.uiState.value.filter.dateTo },
            onDateFrom = { scheduleViewModel.selectDateFrom(it) },
            onDateTo = { scheduleViewModel.selectDateTo(it) },
            onClear = { scheduleViewModel.clearDates() }
        )
    }

    override fun renderFilters(state: ReportUiState<ScheduleChangeReportRow, ScheduleChangeReportFilter>) {
        val filter = state.filter
        (binding.chipTypes.getChildAt(types.indexOf(filter.type)) as? Chip)?.isChecked = true

        val options = state.options
        binding.filterRow.isVisible = options.teachers.isNotEmpty() || options.classGroups.isNotEmpty()
        classDropdown?.render(
            listOf<Pair<Int?, String>>(null to getString(R.string.admin_filter_all_classes)) +
                options.classGroups.map { it.id to it.name },
            filter.classGroupId
        )
        teacherDropdown?.render(
            listOf<Pair<Int?, String>>(null to getString(R.string.admin_filter_all_teachers)) +
                options.teachers.map { it.id to it.name },
            filter.teacherId
        )

        renderDateRange(filter.dateFrom, filter.dateTo)
    }

    override fun buildChartSpec(
        state: ReportUiState<ScheduleChangeReportRow, ScheduleChangeReportFilter>
    ): ReportChartSpec {
        val ctx = requireContext()
        val subCount = state.rows.count { it.type == ChangeType.SUBSTITUTE }
        val resCount = state.rows.count { it.type == ChangeType.RESCHEDULE }
        val canCount = state.rows.count { it.type == ChangeType.CANCEL }
        return ReportChartSpec(
            title = "Sebaran Jenis Perubahan Jadwal",
            subtitle = "Berdasarkan ${state.rows.size} perubahan yang ditampilkan",
            kind = ReportChartKind.DONUT,
            centerLabel = "${state.rows.size}",
            centerSubLabel = "Perubahan",
            segments = listOf(
                ChartSegment("Ganti Guru", subCount.toFloat(), "$subCount", ContextCompat.getColor(ctx, R.color.change_substitute)),
                ChartSegment("Pindah Jadwal", resCount.toFloat(), "$resCount", ContextCompat.getColor(ctx, R.color.change_reschedule)),
                ChartSegment("Dibatalkan", canCount.toFloat(), "$canCount", ContextCompat.getColor(ctx, R.color.change_cancel))
            )
        )
    }

    override fun buildExportPreview(
        state: ReportUiState<ScheduleChangeReportRow, ScheduleChangeReportFilter>,
        fileName: String
    ): ReportExportPreviewSpec {
        val filters = buildList {
            state.filter.classGroupId?.let { id ->
                state.options.classGroups.firstOrNull { it.id == id }?.name?.let { add("Kelas: $it") }
            }
            state.filter.teacherId?.let { id ->
                state.options.teachers.firstOrNull { it.id == id }?.name?.let { add("Guru: $it") }
            }
            state.filter.type?.let {
                add(
                    "Jenis: " + when (it) {
                        ChangeType.SUBSTITUTE -> "Ganti Guru"
                        ChangeType.RESCHEDULE -> "Pindah Jadwal"
                        ChangeType.CANCEL -> "Dibatalkan"
                    }
                )
            }
            if (state.filter.dateFrom != null || state.filter.dateTo != null) {
                add(
                    "Tanggal: ${state.filter.dateFrom?.let(DateTimeWib::formatDate) ?: "..."} – " +
                        (state.filter.dateTo?.let(DateTimeWib::formatDate) ?: "...")
                )
            }
        }
        return ReportExportPreviewSpec(
            reportTitle = getString(titleRes),
            fileName = fileName,
            totalCount = state.total,
            filters = filters,
            columns = listOf("Tanggal", "Kelas", "Mapel", "Jenis", "Guru Asli", "Keterangan"),
            rows = state.rows.map { row ->
                val typeLabel = when (row.type) {
                    ChangeType.SUBSTITUTE -> "Ganti Guru"
                    ChangeType.RESCHEDULE -> "Pindah Jadwal"
                    ChangeType.CANCEL -> "Dibatalkan"
                }
                val detail = when (row.type) {
                    ChangeType.SUBSTITUTE -> row.substituteTeacherName?.let { "Pengganti: $it" } ?: row.description
                    ChangeType.RESCHEDULE -> {
                        val moved = listOfNotNull(
                            row.newDate?.let(DateTimeWib::formatDate),
                            if (row.newStart != null && row.newEnd != null) "${row.newStart}–${row.newEnd}" else null
                        ).joinToString(" ")
                        moved.ifBlank { row.description }
                    }
                    ChangeType.CANCEL -> row.description
                }
                listOf(
                    DateTimeWib.formatDate(row.date),
                    row.classGroupName,
                    row.subjectName,
                    typeLabel,
                    row.originalTeacherName,
                    detail
                )
            }
        )
    }

    override fun releaseFilters() {
        classDropdown = null
        teacherDropdown = null
    }
}
