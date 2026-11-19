package id.sch.santaagnes.sim.ui.admin.elective

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.databinding.ItemSubjectPackageBinding
import id.sch.santaagnes.sim.domain.model.SubjectPackage

class SubjectPackageAdapter(
    private val onEdit: (SubjectPackage) -> Unit,
    private val onDelete: (SubjectPackage) -> Unit
) : ListAdapter<SubjectPackage, SubjectPackageAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemSubjectPackageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubjectPackage) {
            binding.tvPackageName.text = item.name
            binding.tvCapacityInfo.text =
                "TA ${item.academicYearName} · Kapasitas ${item.capacity} siswa · ${item.selectionCount} pemilih"

            binding.tvSubjectsSummary.text = if (item.subjects.isEmpty()) {
                "Belum ada mata pelajaran"
            } else {
                item.subjects.joinToString("\n") { sub ->
                    "• [${sub.code}] ${sub.name} (${sub.type})"
                }
            }

            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubjectPackageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SubjectPackage>() {
        override fun areItemsTheSame(
            oldItem: SubjectPackage,
            newItem: SubjectPackage
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: SubjectPackage,
            newItem: SubjectPackage
        ): Boolean = oldItem == newItem
    }
}
