package id.sch.santaagnes.sim.ui.admin.master.students

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
            val context = binding.root.context
            binding.tvStudentName.text = student.name
            val classLabel = student.className ?: "Belum ada kelas"
            binding.tvStudentNisClass.text = "NIS: ${student.nis} | $classLabel"
            binding.tvStudentEmail.text = student.email
            binding.tvStudentMetaRight.text = student.birthDate ?: student.phone.orEmpty()

            binding.tvStudentStatus.text = context.getString(
                if (student.isActive) R.string.status_active else R.string.status_inactive
            )
            binding.tvStudentStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (student.isActive) R.color.agnesian_button else R.color.change_cancel
                )
            )

            binding.btnGuardians.setOnClickListener { onManageGuardians(student) }
            binding.btnEdit.setOnClickListener { onEdit(student) }
            binding.btnDelete.setOnClickListener { onDelete(student) }
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
        override fun areItemsTheSame(oldItem: MasterStudent, newItem: MasterStudent): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MasterStudent, newItem: MasterStudent): Boolean =
            oldItem == newItem
    }
}
