package id.sch.santaagnes.sim.ui.admin.master.teachers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemAdminTeacherBinding
import id.sch.santaagnes.sim.domain.model.MasterTeacher

class AdminTeacherAdapter(
    private val onViewAssignments: (MasterTeacher) -> Unit,
    private val onEdit: (MasterTeacher) -> Unit,
    private val onDelete: (MasterTeacher) -> Unit
) : ListAdapter<MasterTeacher, AdminTeacherAdapter.TeacherViewHolder>(DiffCallback) {

    inner class TeacherViewHolder(
        private val binding: ItemAdminTeacherBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(teacher: MasterTeacher) {
            val ctx = binding.root.context
            binding.tvTeacherName.text = teacher.name
            binding.tvTeacherNip.text = "NIP: ${teacher.employeeNumber} · ${teacher.email}"

            binding.tvTeacherStatus.text = ctx.getString(
                if (teacher.isActive) R.string.status_active else R.string.status_inactive
            )
            binding.tvTeacherStatus.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (teacher.isActive) R.color.agnesian_button else R.color.change_cancel
                )
            )

            // Bedakan visibilitas dan deskripsi akses antara Wali Kelas vs Guru Pengajar Biasa
            if (teacher.isHomeroomTeacher) {
                binding.tvTeacherRoleBadge.setText(R.string.teacher_badge_homeroom)
                binding.tvTeacherRoleBadge.setBackgroundResource(R.drawable.bg_badge_role)
                binding.tvTeacherRoleBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.agnesian_blue)
                )
                binding.tvTeacherAccessDesc.setText(R.string.teacher_access_homeroom)
            } else {
                binding.tvTeacherRoleBadge.setText(R.string.teacher_badge_regular)
                binding.tvTeacherRoleBadge.setBackgroundResource(R.drawable.bg_badge)
                binding.tvTeacherRoleBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.agnesian_text)
                )
                binding.tvTeacherAccessDesc.setText(R.string.teacher_access_regular)
            }

            binding.btnAssignments.setOnClickListener { onViewAssignments(teacher) }
            binding.btnEdit.setOnClickListener { onEdit(teacher) }
            binding.btnDelete.setOnClickListener { onDelete(teacher) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val binding = ItemAdminTeacherBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TeacherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MasterTeacher>() {
        override fun areItemsTheSame(oldItem: MasterTeacher, newItem: MasterTeacher): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MasterTeacher, newItem: MasterTeacher): Boolean =
            oldItem == newItem
    }
}
