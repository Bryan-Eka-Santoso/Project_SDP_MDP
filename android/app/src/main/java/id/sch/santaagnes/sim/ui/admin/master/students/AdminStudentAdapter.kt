package id.sch.santaagnes.sim.ui.admin.master.students

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemAdminStudentBinding
import id.sch.santaagnes.sim.domain.model.MasterStudent

class AdminStudentAdapter(
    private val onEdit: (MasterStudent) -> Unit,
    private val onManageGuardians: (MasterStudent) -> Unit,
    private val onDelete: (MasterStudent) -> Unit
) : ListAdapter<MasterStudent, AdminStudentAdapter.StudentViewHolder>(DiffCallback) {

    inner class StudentViewHolder(
        private val binding: ItemAdminStudentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(student: MasterStudent) {
            binding.tvStudentName.text = student.name
            binding.tvStudentNisClass.text = "NIS: ${student.nis} · ${student.className ?: "Belum ada kelas"}"
            binding.tvStudentEmail.text = student.email

            if (student.isActive) {
                binding.tvStudentStatusBadge.text = binding.root.context.getString(R.string.status_active)
                binding.tvStudentStatusBadge.setBackgroundResource(R.drawable.bg_badge_active)
                binding.tvStudentStatusBadge.setTextColor(0xFF137333.toInt())
            } else {
                binding.tvStudentStatusBadge.text = binding.root.context.getString(R.string.status_inactive)
                binding.tvStudentStatusBadge.setBackgroundResource(R.drawable.bg_badge_inactive)
                binding.tvStudentStatusBadge.setTextColor(0xFFC5221F.toInt())
            }

            binding.tvGuardianBadge.isVisible = student.guardianCount > 0
            binding.tvGuardianBadge.text = "${student.guardianCount} Wali Murid"

            binding.btnOptions.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Ubah Data Siswa")
                popup.menu.add(0, 2, 1, "Kelola Wali Murid")
                popup.menu.add(0, 3, 2, "Hapus Siswa")

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { onEdit(student); true }
                        2 -> { onManageGuardians(student); true }
                        3 -> { onDelete(student); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val binding = ItemAdminStudentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MasterStudent>() {
        override fun areItemsTheSame(oldItem: MasterStudent, newItem: MasterStudent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MasterStudent, newItem: MasterStudent): Boolean {
            return oldItem == newItem
        }
    }
}
