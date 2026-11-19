package id.sch.santaagnes.sim.ui.admin.master.teachers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.databinding.ItemAdminTeachingAssignmentBinding
import id.sch.santaagnes.sim.domain.model.MasterTeachingAssignment

class AdminTeachingAssignmentAdapter(
    private val onEditTeacher: (MasterTeachingAssignment) -> Unit,
    private val onDelete: (MasterTeachingAssignment) -> Unit
) : ListAdapter<MasterTeachingAssignment, AdminTeachingAssignmentAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemAdminTeachingAssignmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MasterTeachingAssignment) {
            binding.tvTeacherName.text = item.teacherName
            binding.tvSemesterTag.text = if (item.semesterType == "ODD") "Sem. Ganjil" else "Sem. Genap"
            val yearSuffix = item.academicYearName?.let { " ($it)" }.orEmpty()
            binding.tvSubjectClass.text =
                "Mata Pelajaran: ${item.subjectName}\nKelas: ${item.className}$yearSuffix"

            binding.btnEdit.setOnClickListener { onEditTeacher(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminTeachingAssignmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MasterTeachingAssignment>() {
        override fun areItemsTheSame(
            oldItem: MasterTeachingAssignment,
            newItem: MasterTeachingAssignment
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: MasterTeachingAssignment,
            newItem: MasterTeachingAssignment
        ): Boolean = oldItem == newItem
    }
}
