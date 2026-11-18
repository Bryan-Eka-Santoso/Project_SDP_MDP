package id.sch.santaagnes.sim.ui.admin.master.classes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemAdminClassBinding
import id.sch.santaagnes.sim.domain.model.MasterClassGroup

class AdminClassAdapter(
    private val onManageMembers: (MasterClassGroup) -> Unit,
    private val onSetHomeroom: (MasterClassGroup) -> Unit,
    private val onEdit: (MasterClassGroup) -> Unit,
    private val onDelete: (MasterClassGroup) -> Unit
) : ListAdapter<MasterClassGroup, AdminClassAdapter.ClassViewHolder>(DiffCallback) {

    inner class ClassViewHolder(
        private val binding: ItemAdminClassBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MasterClassGroup) {
            val ctx = binding.root.context
            binding.tvClassName.text = "Kelas ${item.name} (Tingkat ${item.gradeLevel})"
            binding.tvClassYear.text = item.academicYearName
            binding.tvClassYear.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (item.isAcademicYearActive) R.color.agnesian_button else R.color.agnesian_muted
                )
            )
            binding.tvHomeroom.text = item.homeroomTeacherName?.let {
                "Wali Kelas: $it"
            } ?: "Wali Kelas: Belum diatur"

            binding.tvStudentCount.text = "Jumlah Siswa: ${item.memberCount} / ${item.capacity} Siswa"

            binding.btnMembers.setOnClickListener { onManageMembers(item) }
            binding.btnHomeroom.setOnClickListener { onSetHomeroom(item) }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val binding = ItemAdminClassBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ClassViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MasterClassGroup>() {
        override fun areItemsTheSame(oldItem: MasterClassGroup, newItem: MasterClassGroup): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MasterClassGroup, newItem: MasterClassGroup): Boolean =
            oldItem == newItem
    }
}
