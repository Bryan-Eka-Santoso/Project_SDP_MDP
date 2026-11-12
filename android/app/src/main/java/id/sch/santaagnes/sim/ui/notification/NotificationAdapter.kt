package id.sch.santaagnes.sim.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemNotificationBinding
import id.sch.santaagnes.sim.domain.model.AppNotification
import id.sch.santaagnes.sim.domain.model.NotificationType

class NotificationAdapter(
    private val onClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder =
        NotificationViewHolder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class NotificationViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppNotification, onClick: (AppNotification) -> Unit) {
            val context = binding.root.context
            binding.tvTitle.text = item.title
            binding.tvMessage.text = item.message
            binding.tvMeta.text = context.getString(
                R.string.notification_meta,
                context.getString(typeLabel(item.type)),
                DateTimeWib.formatTimestamp(item.receivedAtMillis)
            )
            binding.unreadDot.visibility = if (item.isRead) View.INVISIBLE else View.VISIBLE
            binding.root.setOnClickListener { onClick(item) }
        }

        @StringRes
        private fun typeLabel(type: NotificationType): Int = when (type) {
            NotificationType.SCHEDULE_CHANGE -> R.string.notification_type_schedule
            NotificationType.TASK -> R.string.notification_type_task
            NotificationType.ANNOUNCEMENT -> R.string.notification_type_announcement
            NotificationType.VIOLATION -> R.string.notification_type_violation
            NotificationType.LEAVE_REQUEST -> R.string.notification_type_leave
            NotificationType.BOOKING -> R.string.notification_type_booking
            NotificationType.BILL_OVERDUE -> R.string.notification_type_bill
            NotificationType.OTHER -> R.string.notification_type_other
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean =
            oldItem == newItem
    }
}
