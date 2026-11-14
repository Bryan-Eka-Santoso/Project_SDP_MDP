package id.sch.santaagnes.sim.ui.admin.master.users

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
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
            binding.tvUserName.text = user.name
            binding.tvUserEmail.text = user.email
            binding.tvRoleBadge.text = user.role

            if (user.isActive) {
                binding.tvStatusBadge.text = binding.root.context.getString(R.string.status_active)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_active)
                binding.tvStatusBadge.setTextColor(0xFF137333.toInt())
            } else {
                binding.tvStatusBadge.text = binding.root.context.getString(R.string.status_inactive)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_inactive)
                binding.tvStatusBadge.setTextColor(0xFFC5221F.toInt())
            }

            binding.btnOptions.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Ubah Data")
                popup.menu.add(0, 2, 1, if (user.isActive) "Blokir Akun" else "Buka Blokir")
                popup.menu.add(0, 3, 2, "Reset Password")
                popup.menu.add(0, 4, 3, "Hapus Akun")

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { onEdit(user); true }
                        2 -> { onToggleStatus(user); true }
                        3 -> { onResetPassword(user); true }
                        4 -> { onDelete(user); true }
                        else -> false
                    }
                }
                popup.show()
            }
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
        override fun areItemsTheSame(oldItem: MasterUser, newItem: MasterUser): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MasterUser, newItem: MasterUser): Boolean {
            return oldItem == newItem
        }
    }
}
