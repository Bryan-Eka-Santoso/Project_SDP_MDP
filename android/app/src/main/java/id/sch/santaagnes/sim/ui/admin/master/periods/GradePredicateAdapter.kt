package id.sch.santaagnes.sim.ui.admin.master.periods

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.databinding.ItemGradePredicateBinding
import id.sch.santaagnes.sim.domain.model.GradePredicateItem

class GradePredicateAdapter(
    private val onEdit: (Int, GradePredicateItem) -> Unit,
    private val onDelete: (Int) -> Unit
) : ListAdapter<GradePredicateItem, GradePredicateAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemGradePredicateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GradePredicateItem, position: Int) {
            binding.tvLabel.text = item.label
            val scoreText = GradePredicateViewModel.formatScore(item.minScore)
            binding.tvMinScore.text = "Nilai minimum >= $scoreText"
            binding.tvDescription.isVisible = !item.description.isNullOrBlank()
            binding.tvDescription.text = item.description

            binding.btnEdit.setOnClickListener { onEdit(position, item) }
            binding.btnDelete.setOnClickListener { onDelete(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGradePredicateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<GradePredicateItem>() {
        override fun areItemsTheSame(
            oldItem: GradePredicateItem,
            newItem: GradePredicateItem
        ): Boolean = oldItem.label == newItem.label && oldItem.minScore == newItem.minScore

        override fun areContentsTheSame(
            oldItem: GradePredicateItem,
            newItem: GradePredicateItem
        ): Boolean = oldItem == newItem
    }
}
