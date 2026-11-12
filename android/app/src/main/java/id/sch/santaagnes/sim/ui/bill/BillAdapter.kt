package id.sch.santaagnes.sim.ui.bill

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Rupiah
import id.sch.santaagnes.sim.databinding.ItemBillBinding
import id.sch.santaagnes.sim.domain.model.Bill
import id.sch.santaagnes.sim.domain.model.BillStatus

class BillAdapter : ListAdapter<Bill, BillAdapter.BillViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder =
        BillViewHolder(ItemBillBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class BillViewHolder(private val binding: ItemBillBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(bill: Bill) {
            val context = binding.root.context

            val student = bill.student
            binding.tvStudent.isVisible = student != null
            if (student != null) {
                binding.tvStudent.text = listOfNotNull(
                    student.name,
                    student.nis,
                    student.classNames.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ).joinToString(" · ")
            }

            binding.tvType.text = bill.paymentType
            binding.tvAmount.text = Rupiah.format(bill.amount)
            binding.tvCode.text = context.getString(R.string.bill_code, bill.code)
            binding.tvDue.text = when (val paid = bill.paidDate) {
                // Tanggal bayar opsional pada rekap; tanpa itu cukup jatuh temponya.
                null -> context.getString(R.string.bill_due, DateTimeWib.formatDate(bill.dueDate))
                else -> context.getString(
                    R.string.bill_due_paid,
                    DateTimeWib.formatDate(bill.dueDate),
                    DateTimeWib.formatDate(paid)
                )
            }

            val (label, color) = when (bill.status) {
                BillStatus.PAID -> R.string.bill_status_paid to R.color.bill_paid
                BillStatus.OVERDUE -> R.string.bill_status_overdue to R.color.change_cancel
                BillStatus.UNPAID -> R.string.bill_status_unpaid to R.color.change_substitute
            }
            binding.tvBadge.setText(label)
            binding.tvBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Bill>() {
        override fun areItemsTheSame(oldItem: Bill, newItem: Bill): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Bill, newItem: Bill): Boolean = oldItem == newItem
    }
}
