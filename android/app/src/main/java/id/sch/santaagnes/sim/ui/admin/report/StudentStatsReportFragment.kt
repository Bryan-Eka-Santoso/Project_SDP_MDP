package id.sch.santaagnes.sim.ui.admin.report

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.domain.model.StudentStatsFilter
import id.sch.santaagnes.sim.domain.model.StudentStatsRow

/** Laporan statistik kesiswaan per kelas pada semester aktif (F-LAP-10): filter kelas dan tingkat. */
@AndroidEntryPoint
class StudentStatsReportFragment : ReportFragment<StudentStatsRow, StudentStatsFilter>() {

    override val viewModel: ReportViewModel<StudentStatsRow, StudentStatsFilter> by
        viewModels<StudentStatsReportViewModel>()

    override val adapter = StudentStatsReportAdapter()
    override val titleRes = R.string.report_student_stats_title
    override val reportType = "student-stats"
    override val totalRes = R.string.report_total_classes

    private val statsViewModel get() = viewModel as StudentStatsReportViewModel

    private var classDropdown: ReportDropdown? = null
    private var renderedLevels: List<String?> = emptyList()

    override fun setupFilters() {
        binding.layoutTeacher.isVisible = false
        classDropdown = ReportDropdown(binding.inputClass) { statsViewModel.selectClassGroup(it) }
    }

    override fun renderFilters(state: ReportUiState<StudentStatsRow, StudentStatsFilter>) {
        val classes = state.options.classGroups
        binding.filterRow.isVisible = classes.isNotEmpty()
        classDropdown?.render(
            listOf<Pair<Int?, String>>(null to getString(R.string.admin_filter_all_classes)) +
                classes.map { it.id to it.name },
            state.filter.classGroupId
        )

        val gradeLevels = state.options.gradeLevels
        val showGrades = gradeLevels.isNotEmpty() || state.filter.gradeLevel != null
        binding.scrollTypes.isVisible = showGrades
        if (!showGrades) return

        val levels: List<String?> = listOf(null) + (
            if (state.filter.gradeLevel != null && state.filter.gradeLevel !in gradeLevels) {
                gradeLevels + state.filter.gradeLevel
            } else {
                gradeLevels
            }
        )
        if (levels != renderedLevels) {
            renderedLevels = levels
            binding.chipTypes.removeAllViews()
            levels.forEach { level ->
                val chip = Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = if (level == null) {
                        getString(R.string.report_filter_all_grades)
                    } else {
                        getString(R.string.report_filter_grade_level, level)
                    }
                    isCheckable = true
                    setOnClickListener { statsViewModel.selectGradeLevel(level) }
                }
                binding.chipTypes.addView(chip)
            }
        }
        val selectedIdx = levels.indexOf(state.filter.gradeLevel)
        if (selectedIdx >= 0) {
            (binding.chipTypes.getChildAt(selectedIdx) as? Chip)?.isChecked = true
        }
    }

    override fun buildChartSpec(state: ReportUiState<StudentStatsRow, StudentStatsFilter>): ReportChartSpec {
        val ctx = requireContext()
        val cGood = ContextCompat.getColor(ctx, R.color.bill_paid)
        val cWarn = ContextCompat.getColor(ctx, R.color.change_substitute)
        val cBad = ContextCompat.getColor(ctx, R.color.change_cancel)
        val cBlue = ContextCompat.getColor(ctx, R.color.agnesian_blue)
        val totalStudents = state.rows.sumOf { it.studentCount }
        val totalViolations = state.rows.sumOf { it.violationPoints }
        return ReportChartSpec(
            title = "Tingkat Kehadiran per Kelas (%)",
            subtitle = "Semester aktif · ${state.rows.size} kelas ditampilkan",
            kind = ReportChartKind.HORIZONTAL_BAR,
            kpis = listOf(
                ChartKpi("Total Siswa", "$totalStudents siswa", cBlue),
                ChartKpi("Total Poin Pelanggaran", "$totalViolations poin", if (totalViolations > 0) cBad else cGood)
            ),
            segments = state.rows.take(6).map { row ->
                val color = when {
                    row.attendanceTotal == 0 -> ContextCompat.getColor(ctx, R.color.agnesian_muted)
                    row.attendanceRate >= 90.0 -> cGood
                    row.attendanceRate >= 75.0 -> cWarn
                    else -> cBad
                }
                ChartSegment(
                    label = "${row.className} (${row.studentCount} siswa)",
                    value = row.attendanceRate.toFloat(),
                    displayValue = if (row.attendanceTotal == 0) "0%" else "${formatReportDecimal(row.attendanceRate)}%",
                    color = color
                )
            }
        )
    }

    override fun releaseFilters() {
        classDropdown = null
        renderedLevels = emptyList()
    }
}
