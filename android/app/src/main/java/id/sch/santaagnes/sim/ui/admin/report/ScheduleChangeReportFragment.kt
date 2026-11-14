package id.sch.santaagnes.sim.ui.admin.report

import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportFilter
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

        binding.dateRow.isVisible = true
        binding.inputDateFrom.setOnClickListener {
            pickDate(R.string.report_filter_date_from, currentFilter().dateFrom) { scheduleViewModel.selectDateFrom(it) }
        }
        binding.inputDateTo.setOnClickListener {
            pickDate(R.string.report_filter_date_to, currentFilter().dateTo) { scheduleViewModel.selectDateTo(it) }
        }
        binding.btnClearDates.setOnClickListener { scheduleViewModel.clearDates() }
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

        binding.inputDateFrom.setText(filter.dateFrom?.let(DateTimeWib::formatDate).orEmpty())
        binding.inputDateTo.setText(filter.dateTo?.let(DateTimeWib::formatDate).orEmpty())
        binding.btnClearDates.isVisible = filter.dateFrom != null || filter.dateTo != null
    }

    override fun releaseFilters() {
        classDropdown = null
        teacherDropdown = null
    }

    private fun currentFilter(): ScheduleChangeReportFilter = scheduleViewModel.uiState.value.filter

    private fun pickDate(titleRes: Int, current: LocalDate?, onPicked: (LocalDate) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .setSelection((current ?: DateTimeWib.today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(childFragmentManager, "date")
    }
}
