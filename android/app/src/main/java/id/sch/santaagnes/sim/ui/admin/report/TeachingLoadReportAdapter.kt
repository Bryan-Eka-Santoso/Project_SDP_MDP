package id.sch.santaagnes.sim.ui.admin.report

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemReportDayBarBinding
import id.sch.santaagnes.sim.databinding.ItemReportTeachingLoadBinding
import id.sch.santaagnes.sim.domain.model.TeachingLoadRow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.util.Locale

/** Jam dengan koma desimal dan tanpa nol di belakang: 18, 18,5, 4,75. */
internal fun formatHours(hours: Double): String =
    DecimalFormat("0.##", DecimalFormatSymbols(Locale("id", "ID"))).format(hours)

/** Kartu baris laporan beban mengajar guru (F-LAP-09), dengan batang jam per hari Senin sampai Sabtu. */
class TeachingLoadReportAdapter :
    ListAdapter<TeachingLoadRow, TeachingLoadReportAdapter.RowViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(ItemReportTeachingLoadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RowViewHolder(
        private val binding: ItemReportTeachingLoadBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: TeachingLoadRow) {
            val context = binding.root.context
            binding.tvName.text = row.teacherName
            binding.tvNip.isVisible = row.employeeNumber != null
            binding.tvNip.text = row.employeeNumber?.let { context.getString(R.string.report_load_nip, it) }
            binding.tvHours.text = context.getString(R.string.report_load_hours, formatHours(row.totalHours))

            if (row.sessionsPerWeek == 0) {
                binding.tvSummary.setText(R.string.report_load_no_schedule)
            } else {
                binding.tvSummary.text = context.getString(
                    R.string.report_load_summary,
                    row.sessionsPerWeek,
                    row.classCount
                )
            }
            binding.tvSubjects.isVisible = row.subjectNames.isNotEmpty()
            binding.tvSubjects.text = row.subjectNames.joinToString(", ")
            bindDays(row)
        }

        private fun bindDays(row: TeachingLoadRow) {
            val inflater = LayoutInflater.from(binding.root.context)
            val density = binding.root.resources.displayMetrics.density
            binding.daysRow.removeAllViews()
            DayOfWeek.entries.take(row.hoursPerDay.size).forEachIndexed { index, day ->
                val hours = row.hoursPerDay[index]
                val bar = ItemReportDayBarBinding.inflate(inflater, binding.daysRow, false)
                bar.tvDayName.text = DateTimeWib.dayName(day).take(DAY_LABEL_LENGTH)
                bar.tvDayHours.text = formatHours(hours)
                // Skala tetap (bukan relatif per guru) supaya batang antar guru bisa dibandingkan.
                val fraction = (hours / MAX_DAY_HOURS).coerceIn(0.0, 1.0)
                bar.viewBar.layoutParams = bar.viewBar.layoutParams.also {
                    it.height = ((MIN_BAR_DP + fraction * (MAX_BAR_DP - MIN_BAR_DP)) * density).toInt()
                }
                bar.viewBar.alpha = if (hours > 0) 1f else EMPTY_ALPHA
                binding.daysRow.addView(bar.root)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TeachingLoadRow>() {
        override fun areItemsTheSame(old: TeachingLoadRow, new: TeachingLoadRow) = old.teacherId == new.teacherId
        override fun areContentsTheSame(old: TeachingLoadRow, new: TeachingLoadRow) = old == new
    }

    private companion object {
        const val DAY_LABEL_LENGTH = 3
        // Jadwal sekolah biasanya 1,5-4,5 jam mengajar per guru per hari; skala 6 jam = batang tertinggi.
        const val MAX_DAY_HOURS = 6.0
        const val MIN_BAR_DP = 2
        const val MAX_BAR_DP = 48
        const val EMPTY_ALPHA = 0.25f
    }
}
