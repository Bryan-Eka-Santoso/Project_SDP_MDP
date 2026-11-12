package id.sch.santaagnes.sim.ui.admin.schedule

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
import id.sch.santaagnes.sim.databinding.ItemAdminChangeBinding
import id.sch.santaagnes.sim.domain.model.AdminChange
import id.sch.santaagnes.sim.domain.model.ChangeType

class AdminChangeAdapter(
    private val onEdit: (AdminChange) -> Unit,
    private val onDelete: (AdminChange) -> Unit
) : ListAdapter<AdminChange, AdminChangeAdapter.ChangeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChangeViewHolder =
        ChangeViewHolder(ItemAdminChangeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ChangeViewHolder, position: Int) {
        holder.bind(getItem(position), onEdit, onDelete)
    }

    class ChangeViewHolder(
        private val binding: ItemAdminChangeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(change: AdminChange, onEdit: (AdminChange) -> Unit, onDelete: (AdminChange) -> Unit) {
            val context = binding.root.context
            val slot = change.slot
            binding.tvDate.text = DateTimeWib.formatDate(change.date)
            binding.tvSlot.text = context.getString(R.string.task_subject_class, slot.subjectName, slot.classGroupName)

            val (label, color) = when (change.type) {
                ChangeType.SUBSTITUTE -> R.string.schedule_change_substitute to R.color.change_substitute
                ChangeType.RESCHEDULE -> R.string.schedule_change_reschedule to R.color.change_reschedule
                ChangeType.CANCEL -> R.string.schedule_change_cancel to R.color.change_cancel
            }
            binding.tvBadge.setText(label)
            binding.tvBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))

            val lines = detailLines(change)
            binding.tvDetail.isVisible = lines.isNotEmpty()
            binding.tvDetail.text = lines.joinToString("\n")
            binding.tvDescription.text = change.description
            binding.btnEdit.setOnClickListener { onEdit(change) }
            binding.btnDelete.setOnClickListener { onDelete(change) }
        }

        // Nilai semula selalu tampil sebagai pembanding; nilai baru hanya bila jenisnya mengubah itu.
        private fun detailLines(change: AdminChange): List<String> {
            val context = binding.root.context
            val slot = change.slot
            val lines = mutableListOf<String>()
            when (change.type) {
                ChangeType.SUBSTITUTE -> {
                    lines += context.getString(R.string.schedule_original_teacher, slot.teacherName)
                    change.substituteTeacherName?.let { lines += context.getString(R.string.change_substitute_line, it) }
                }
                ChangeType.RESCHEDULE -> {
                    val start = change.newStartTime
                    val end = change.newEndTime
                    if (start != null && end != null) {
                        lines += context.getString(
                            R.string.change_new_time,
                            DateTimeWib.formatTime(start),
                            DateTimeWib.formatTime(end)
                        )
                    }
                    change.newRoomName?.let { lines += context.getString(R.string.change_new_room, it) }
                }
                ChangeType.CANCEL -> Unit
            }
            return lines
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AdminChange>() {
        override fun areItemsTheSame(oldItem: AdminChange, newItem: AdminChange): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AdminChange, newItem: AdminChange): Boolean = oldItem == newItem
    }
}
