package id.sch.santaagnes.sim.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemAcademicEventBinding
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import java.time.LocalDate

class AcademicEventAdapter(
    private val isAdmin: () -> Boolean,
    private val onSaveToDeviceCalendar: (AcademicEvent) -> Unit,
    private val onEdit: (AcademicEvent) -> Unit,
    private val onDelete: (AcademicEvent) -> Unit
) : ListAdapter<AcademicEvent, AcademicEventAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemAcademicEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AcademicEvent) {
            val ctx = binding.root.context
            binding.tvTypeBadge.text = item.type.label
            val typeColor = when (item.type) {
                AcademicEventType.EXAM -> R.color.change_substitute
                AcademicEventType.HOLIDAY -> R.color.bill_paid
                AcademicEventType.ACTIVITY -> R.color.agnesian_blue
            }
            binding.tvTypeBadge.setTextColor(ContextCompat.getColor(ctx, typeColor))

            binding.tvDateRange.text = runCatching {
                val start = LocalDate.parse(item.startDate)
                val end = LocalDate.parse(item.endDate)
                if (start == end) {
                    DateTimeWib.formatDate(start)
                } else {
                    DateTimeWib.formatRange(start, end)
                }
            }.getOrDefault(
                if (item.startDate == item.endDate) item.startDate else "${item.startDate} – ${item.endDate}"
            )

            binding.tvEventTitle.text = item.title
            binding.tvEventDesc.isVisible = !item.description.isNullOrBlank()
            binding.tvEventDesc.text = item.description

            binding.btnAddToDeviceCalendar.setOnClickListener { onSaveToDeviceCalendar(item) }

            val editable = isAdmin()
            binding.btnEdit.isVisible = editable
            binding.btnDelete.isVisible = editable
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAcademicEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AcademicEvent>() {
        override fun areItemsTheSame(oldItem: AcademicEvent, newItem: AcademicEvent): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AcademicEvent, newItem: AcademicEvent): Boolean =
            oldItem == newItem
    }
}
