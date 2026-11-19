package id.sch.santaagnes.sim.ui.announcement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemAnnouncementBinding
import id.sch.santaagnes.sim.domain.model.Announcement
import java.time.LocalDate

class AnnouncementAdapter(
    private val canManage: (Announcement) -> Boolean,
    private val onOpenAttachment: (Announcement) -> Unit,
    private val onEdit: (Announcement) -> Unit,
    private val onDelete: (Announcement) -> Unit
) : ListAdapter<Announcement, AnnouncementAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemAnnouncementBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Announcement) {
            val ctx = binding.root.context
            val isSchool = item.scope == "SCHOOL"
            binding.tvScopeBadge.text = if (isSchool) {
                "Sekolah"
            } else {
                "Kelas ${item.className ?: "-"}"
            }
            binding.tvScopeBadge.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (isSchool) R.color.agnesian_blue else R.color.change_substitute
                )
            )
            binding.tvDate.text = runCatching {
                DateTimeWib.formatDate(LocalDate.parse(item.date))
            }.getOrDefault(item.date)

            binding.tvTitle.text = item.title
            binding.tvAuthor.text = "Oleh ${item.authorName}"
            binding.tvContent.text = item.content

            val hasAttachment = !item.attachmentUrl.isNullOrBlank()
            binding.btnOpenAttachment.isVisible = hasAttachment
            binding.btnOpenAttachment.setOnClickListener { onOpenAttachment(item) }

            val manageable = canManage(item)
            binding.btnEdit.isVisible = manageable
            binding.btnDelete.isVisible = manageable
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnnouncementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Announcement>() {
        override fun areItemsTheSame(oldItem: Announcement, newItem: Announcement): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Announcement, newItem: Announcement): Boolean =
            oldItem == newItem
    }
}
