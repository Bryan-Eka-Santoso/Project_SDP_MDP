package id.sch.santaagnes.sim.ui.admin.master.users

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemAdminUserBinding
import id.sch.santaagnes.sim.domain.model.MasterUser

class AdminUserAdapter(
    private val onEdit: (MasterUser) -> Unit,
    private val onToggleStatus: (MasterUser) -> Unit,
    private val onResetPassword: (MasterUser) -> Unit,
    private val onDelete: (MasterUser) -> Unit
) : ListAdapter<MasterUser, AdminUserAdapter.UserViewHolder>(DiffCallback) {

    inner class UserViewHolder(
        private val binding: ItemAdminUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: MasterUser) {
            val context = binding.root.context
            binding.tvUserName.text = user.name
            binding.tvUserEmail.text = user.email

            val roleName = when (user.role) {
                "ADMIN" -> context.getString(R.string.role_admin)
                "TEACHER" -> context.getString(R.string.role_teacher)
                "STUDENT" -> context.getString(R.string.role_student)
                "GUARDIAN" -> context.getString(R.string.role_guardian)
                else -> user.role
            }
            val statusText = context.getString(
                if (user.isActive) R.string.status_active else R.string.status_inactive
            )
            binding.tvRoleStatus.text = "$roleName | $statusText"
            binding.tvRoleStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (user.isActive) R.color.agnesian_button else R.color.change_cancel
                )
            )
            binding.tvUserMeta.text = user.createdAt?.take(10).orEmpty()

            binding.btnToggleStatus.setColorFilter(
                ContextCompat.getColor(
                    context,
                    if (user.isActive) R.color.black else R.color.change_cancel
                )
            )

            binding.btnEdit.setOnClickListener { onEdit(user) }
            binding.btnToggleStatus.setOnClickListener { onToggleStatus(user) }
            binding.btnResetPassword.setOnClickListener { onResetPassword(user) }
            binding.btnDelete.setOnClickListener { onDelete(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemAdminUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MasterUser>() {
        override fun areItemsTheSame(oldItem: MasterUser, newItem: MasterUser): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MasterUser, newItem: MasterUser): Boolean =
            oldItem == newItem
    }
}
