package id.sch.santaagnes.sim.ui.admin.report

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemReportStudentStatsBinding
import id.sch.santaagnes.sim.domain.model.StudentStatsRow

/** Kartu baris laporan statistik kesiswaan per kelas (F-LAP-10). */
class StudentStatsReportAdapter :
    ListAdapter<StudentStatsRow, StudentStatsReportAdapter.RowViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(ItemReportStudentStatsBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RowViewHolder(
        private val binding: ItemReportStudentStatsBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: StudentStatsRow) {
            val context = binding.root.context
            binding.tvClassName.text = row.className
            binding.tvGradeHomeroom.text = when (val homeroom = row.homeroomTeacherName) {
                null -> context.getString(R.string.report_stats_grade_no_homeroom, row.gradeLevel)
                else -> context.getString(R.string.report_stats_grade_homeroom, row.gradeLevel, homeroom)
            }

            if (row.attendanceTotal == 0) {
                binding.tvAttendanceRate.setText(R.string.report_stats_no_attendance_rate)
                binding.tvAttendanceRate.setTextColor(ContextCompat.getColor(context, R.color.agnesian_muted))
                binding.tvAttendanceSummary.setText(R.string.report_stats_no_attendance)
                binding.tvAttendanceSummary.setTypeface(null, Typeface.NORMAL)
            } else {
                val rateColor = when {
                    row.attendanceRate >= 90.0 -> R.color.bill_paid
                    row.attendanceRate >= 75.0 -> R.color.change_substitute
                    else -> R.color.change_cancel
                }
                binding.tvAttendanceRate.text = context.getString(
                    R.string.report_stats_attendance_rate,
                    formatReportDecimal(row.attendanceRate)
                )
                binding.tvAttendanceRate.setTextColor(ContextCompat.getColor(context, rateColor))
                binding.tvAttendanceSummary.text = context.getString(
                    R.string.report_stats_attendance,
                    row.presentCount,
                    row.sickCount,
                    row.excusedCount,
                    row.absentCount
                )
                binding.tvAttendanceSummary.setTypeface(null, Typeface.BOLD)
            }

            binding.tvStudentCount.text = context.getString(R.string.report_stats_students, row.studentCount, row.capacity)
            binding.tvStudentCount.setTypeface(null, Typeface.BOLD)
            binding.tvViolations.text = context.getString(
                R.string.report_stats_violations,
                row.violationCount,
                row.violationPoints
            )
            binding.tvViolations.setTypeface(null, if (row.violationCount > 0) Typeface.BOLD else Typeface.NORMAL)
            binding.tvViolations.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (row.violationCount > 0) R.color.change_cancel else R.color.agnesian_muted
                )
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StudentStatsRow>() {
        override fun areItemsTheSame(old: StudentStatsRow, new: StudentStatsRow) =
            old.classGroupId == new.classGroupId

        override fun areContentsTheSame(old: StudentStatsRow, new: StudentStatsRow) = old == new
    }
}
