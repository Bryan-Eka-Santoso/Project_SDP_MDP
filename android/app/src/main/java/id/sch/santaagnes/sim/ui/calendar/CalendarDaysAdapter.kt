package id.sch.santaagnes.sim.ui.calendar

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemCalendarDayBinding
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import java.time.LocalDate

class CalendarDaysAdapter(
    private val onSelectDate: (LocalDate) -> Unit
) : ListAdapter<CalendarDayCell, CalendarDaysAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemCalendarDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cell: CalendarDayCell) {
            val ctx = binding.root.context
            val date = cell.date
            if (date == null) {
                binding.root.background = null
                binding.root.isClickable = false
                binding.root.isFocusable = false
                binding.root.setOnClickListener(null)
                binding.tvDayNumber.text = ""
                binding.dotExam.isVisible = false
                binding.dotHoliday.isVisible = false
                binding.dotActivity.isVisible = false
                return
            }

            binding.tvDayNumber.text = date.dayOfMonth.toString()
            binding.root.isClickable = true
            binding.root.isFocusable = true
            binding.root.setOnClickListener { onSelectDate(date) }

            when {
                cell.isSelected -> {
                    binding.root.setBackgroundResource(R.drawable.bg_calendar_day_selected)
                    binding.tvDayNumber.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                }
                cell.isToday -> {
                    binding.root.setBackgroundResource(R.drawable.bg_calendar_day_today)
                    binding.tvDayNumber.setTextColor(ContextCompat.getColor(ctx, R.color.agnesian_blue))
                }
                cell.isSunday -> {
                    binding.root.background = null
                    binding.tvDayNumber.setTextColor(ContextCompat.getColor(ctx, R.color.change_cancel))
                }
                else -> {
                    binding.root.background = null
                    binding.tvDayNumber.setTextColor(ContextCompat.getColor(ctx, R.color.agnesian_text))
                }
            }

            val selectedTint = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.white))
            binding.dotExam.isVisible = AcademicEventType.EXAM in cell.eventTypes
            binding.dotExam.backgroundTintList = if (cell.isSelected) {
                selectedTint
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.change_substitute))
            }

            binding.dotHoliday.isVisible = AcademicEventType.HOLIDAY in cell.eventTypes
            binding.dotHoliday.backgroundTintList = if (cell.isSelected) {
                selectedTint
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bill_paid))
            }

            binding.dotActivity.isVisible = AcademicEventType.ACTIVITY in cell.eventTypes
            binding.dotActivity.backgroundTintList = if (cell.isSelected) {
                selectedTint
            } else {
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.agnesian_blue))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CalendarDayCell>() {
        override fun areItemsTheSame(oldItem: CalendarDayCell, newItem: CalendarDayCell): Boolean =
            oldItem.date == newItem.date

        override fun areContentsTheSame(oldItem: CalendarDayCell, newItem: CalendarDayCell): Boolean =
            oldItem == newItem
    }
}
