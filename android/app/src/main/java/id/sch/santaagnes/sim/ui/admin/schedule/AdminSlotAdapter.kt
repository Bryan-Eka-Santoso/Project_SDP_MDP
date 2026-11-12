package id.sch.santaagnes.sim.ui.admin.schedule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemAdminSlotBinding
import id.sch.santaagnes.sim.domain.model.AdminSlot

class AdminSlotAdapter(
    private val onEdit: (AdminSlot) -> Unit,
    private val onDelete: (AdminSlot) -> Unit
) : ListAdapter<AdminSlot, AdminSlotAdapter.SlotViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder =
        SlotViewHolder(ItemAdminSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(getItem(position), onEdit, onDelete)
    }

    class SlotViewHolder(
        private val binding: ItemAdminSlotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(slot: AdminSlot, onEdit: (AdminSlot) -> Unit, onDelete: (AdminSlot) -> Unit) {
            val context = binding.root.context
            binding.tvSubject.text = slot.subjectName
            binding.tvTeacherClass.text = context.getString(
                R.string.admin_slot_teacher_class, slot.teacherName, slot.classGroupName
            )
            binding.tvTime.text = context.getString(
                R.string.admin_slot_day_time,
                DateTimeWib.dayName(slot.dayOfWeek),
                DateTimeWib.formatTime(slot.startTime),
                DateTimeWib.formatTime(slot.endTime)
            )
            binding.tvRoom.text = slot.roomName
                ?.let { context.getString(R.string.schedule_room, it) }
                ?: context.getString(R.string.schedule_room_none)
            binding.btnEdit.setOnClickListener { onEdit(slot) }
            binding.btnDelete.setOnClickListener { onDelete(slot) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AdminSlot>() {
        override fun areItemsTheSame(oldItem: AdminSlot, newItem: AdminSlot): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AdminSlot, newItem: AdminSlot): Boolean = oldItem == newItem
    }
}
