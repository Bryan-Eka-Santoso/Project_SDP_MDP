package id.sch.santaagnes.sim.ui.admin.master.periods

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.databinding.ItemAdminAcademicYearBinding
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear

class AdminAcademicYearAdapter(
    private val onSetActive: (MasterAcademicYear) -> Unit,
    private val onManageSemesters: (MasterAcademicYear) -> Unit,
    private val onEdit: (MasterAcademicYear) -> Unit,
    private val onDelete: (MasterAcademicYear) -> Unit
) : ListAdapter<MasterAcademicYear, AdminAcademicYearAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemAdminAcademicYearBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MasterAcademicYear) {
            binding.tvYearName.text = "Tahun Ajaran ${item.name}"
            binding.tvActiveBadge.isVisible = item.isActive
            binding.btnSetActive.isVisible = !item.isActive
            binding.tvYearDates.text =
                "${item.startDate} s.d. ${item.endDate} · ${item.classCount} Kelas · ${item.semesterCount} Semester"

            binding.btnSetActive.setOnClickListener { onSetActive(item) }
            binding.btnSemesters.setOnClickListener { onManageSemesters(item) }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminAcademicYearBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MasterAcademicYear>() {
        override fun areItemsTheSame(
            oldItem: MasterAcademicYear,
            newItem: MasterAcademicYear
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: MasterAcademicYear,
            newItem: MasterAcademicYear
        ): Boolean = oldItem == newItem
    }
}
