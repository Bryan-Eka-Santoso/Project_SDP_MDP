package id.sch.santaagnes.sim.ui.admin.report

import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.domain.model.TeachingLoadFilter
import id.sch.santaagnes.sim.domain.model.TeachingLoadRow

/** Laporan beban mengajar guru (F-LAP-09): hanya filter guru; semester selalu semester aktif. */
@AndroidEntryPoint
class TeachingLoadReportFragment : ReportFragment<TeachingLoadRow, TeachingLoadFilter>() {

    override val viewModel: ReportViewModel<TeachingLoadRow, TeachingLoadFilter> by
        viewModels<TeachingLoadReportViewModel>()

    override val adapter = TeachingLoadReportAdapter()
    override val titleRes = R.string.report_teaching_load_title
    override val reportType = "teaching-load"
    override val totalRes = R.string.report_total_teachers

    private val loadViewModel get() = viewModel as TeachingLoadReportViewModel

    private var teacherDropdown: ReportDropdown? = null

    override fun setupFilters() {
        // Laporan ini tidak punya filter kelas; kolom guru mengisi seluruh baris.
        binding.layoutClass.isVisible = false
        teacherDropdown = ReportDropdown(binding.inputTeacher) { loadViewModel.selectTeacher(it) }
    }

    override fun renderFilters(state: ReportUiState<TeachingLoadRow, TeachingLoadFilter>) {
        binding.filterRow.isVisible = state.options.teachers.isNotEmpty()
        teacherDropdown?.render(
            listOf<Pair<Int?, String>>(null to getString(R.string.admin_filter_all_teachers)) +
                state.options.teachers.map { it.id to it.name },
            state.filter.teacherId
        )
    }

    override fun releaseFilters() {
        teacherDropdown = null
    }
}
