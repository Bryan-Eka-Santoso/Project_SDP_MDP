package id.sch.santaagnes.sim.ui.admin.report

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemReportScheduleChangeBinding
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ScheduleChangeReportRow

/** Kartu baris laporan perubahan jadwal (F-LAP-07). */
class ScheduleChangeReportAdapter :
    ListAdapter<ScheduleChangeReportRow, ScheduleChangeReportAdapter.RowViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(ItemReportScheduleChangeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RowViewHolder(
        private val binding: ItemReportScheduleChangeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ScheduleChangeReportRow) {
            val context = binding.root.context
            binding.tvDate.text = DateTimeWib.formatDate(row.date)
            binding.tvSlot.text = context.getString(R.string.task_subject_class, row.subjectName, row.classGroupName)

            val (label, color) = when (row.type) {
                ChangeType.SUBSTITUTE -> R.string.schedule_change_substitute to R.color.change_substitute
                ChangeType.RESCHEDULE -> R.string.schedule_change_reschedule to R.color.change_reschedule
                ChangeType.CANCEL -> R.string.schedule_change_cancel to R.color.change_cancel
            }
            binding.tvBadge.setText(label)
            binding.tvBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))

            val lines = detailLines(row)
            binding.tvDetail.isVisible = lines.isNotEmpty()
            binding.tvDetail.text = lines.joinToString("\n")
            binding.tvDescription.isVisible = row.description.isNotBlank()
            binding.tvDescription.text = row.description
        }

        // Nilai semula selalu tampil sebagai pembanding; nilai baru hanya bila jenisnya mengubah itu.
        private fun detailLines(row: ScheduleChangeReportRow): List<String> {
            val context = binding.root.context
            val lines = mutableListOf(context.getString(R.string.schedule_original_teacher, row.originalTeacherName))
            when (row.type) {
                ChangeType.SUBSTITUTE ->
                    row.substituteTeacherName?.let { lines += context.getString(R.string.change_substitute_line, it) }
                ChangeType.RESCHEDULE -> {
                    lines += context.getString(
                        R.string.report_original_time,
                        DateTimeWib.formatTime(row.originalStart),
                        DateTimeWib.formatTime(row.originalEnd)
                    )
                    val start = row.newStart
                    val end = row.newEnd
                    if (start != null && end != null) {
                        lines += context.getString(
                            R.string.change_new_time,
                            DateTimeWib.formatTime(start),
                            DateTimeWib.formatTime(end)
                        )
                    }
                    row.originalRoomName?.let { lines += context.getString(R.string.report_original_room, it) }
                    row.newRoomName?.let { lines += context.getString(R.string.change_new_room, it) }
                }
                ChangeType.CANCEL -> Unit
            }
            return lines
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScheduleChangeReportRow>() {
        override fun areItemsTheSame(old: ScheduleChangeReportRow, new: ScheduleChangeReportRow) = old.id == new.id
        override fun areContentsTheSame(old: ScheduleChangeReportRow, new: ScheduleChangeReportRow) = old == new
    }
}
