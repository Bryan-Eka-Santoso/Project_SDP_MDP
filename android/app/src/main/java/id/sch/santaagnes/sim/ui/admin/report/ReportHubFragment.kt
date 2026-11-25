package id.sch.santaagnes.sim.ui.admin.report

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentReportHubBinding
import id.sch.santaagnes.sim.databinding.ItemReportHubTileBinding

/** Daftar laporan admin (F-LAP-01..10 & F-ABS-04) dalam grid 4 kolom (ikon + judul ringkas). */
@AndroidEntryPoint
class ReportHubFragment : Fragment() {

    private var _binding: FragmentReportHubBinding? = null
    private val binding get() = _binding!!

    private data class ReportTile(
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        @ColorRes val tintRes: Int,
        @IdRes val actionId: Int
    )

    private val tiles = listOf(
        ReportTile(R.string.report_tile_grades, R.drawable.ic_grades, R.color.agnesian_blue, R.id.action_reportHub_to_gradesAdminReport),
        ReportTile(R.string.report_tile_attendance, R.drawable.ic_check, R.color.bill_paid, R.id.action_reportHub_to_attendanceAdminReport),
        ReportTile(R.string.report_tile_violations, R.drawable.ic_warning, R.color.change_cancel, R.id.action_reportHub_to_violationReport),
        ReportTile(R.string.report_tile_bookings, R.drawable.ic_facility, R.color.change_substitute, R.id.action_reportHub_to_facilityBookingReport),
        ReportTile(R.string.report_tile_payments, R.drawable.ic_payment, R.color.bill_paid, R.id.action_reportHub_to_paymentStatusReport),
        ReportTile(R.string.report_tile_journals, R.drawable.ic_book, R.color.agnesian_blue, R.id.action_reportHub_to_teachingJournalReport),
        ReportTile(R.string.report_tile_schedule_changes, R.drawable.ic_calendar, R.color.change_substitute, R.id.action_reportHub_to_scheduleChangeReport),
        ReportTile(R.string.report_tile_surveys, R.drawable.ic_package, R.color.agnesian_blue, R.id.action_reportHub_to_electiveSurveyReport),
        ReportTile(R.string.report_tile_teaching_load, R.drawable.ic_school, R.color.agnesian_blue_dark, R.id.action_reportHub_to_teachingLoadReport),
        ReportTile(R.string.report_tile_student_stats, R.drawable.ic_group, R.color.agnesian_blue, R.id.action_reportHub_to_studentStatsReport),
        ReportTile(R.string.report_tile_journal_monitor, R.drawable.ic_bar_chart, R.color.agnesian_blue_dark, R.id.action_reportHub_to_adminJournals)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.rvReportHub.layoutManager = GridLayoutManager(requireContext(), HUB_COLUMNS)
        binding.rvReportHub.adapter = TileAdapter(tiles) { tile ->
            findNavController().navigate(tile.actionId)
        }
    }

    override fun onDestroyView() {
        binding.rvReportHub.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private class TileAdapter(
        private val items: List<ReportTile>,
        private val onClick: (ReportTile) -> Unit
    ) : RecyclerView.Adapter<TileAdapter.TileHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder =
            TileHolder(ItemReportHubTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: TileHolder, position: Int) {
            val item = items[position]
            val context = holder.binding.root.context
            holder.binding.tvLabel.setText(item.labelRes)
            holder.binding.ivIcon.setImageResource(item.iconRes)
            val tint = ContextCompat.getColor(context, item.tintRes)
            holder.binding.ivIcon.imageTintList = ColorStateList.valueOf(tint)
            holder.binding.root.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size

        class TileHolder(val binding: ItemReportHubTileBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private companion object {
        const val HUB_COLUMNS = 4
    }
}
