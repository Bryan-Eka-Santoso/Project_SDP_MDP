package id.sch.santaagnes.sim.ui.admin.peminjaman

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.databinding.ItemPeminjamanBinding

class PeminjamanAdapter(
    private val onApprove: (PeminjamanDto) -> Unit,
    private val onReject: (PeminjamanDto) -> Unit,
    private val onReturn: (PeminjamanDto) -> Unit
) : RecyclerView.Adapter<PeminjamanAdapter.ViewHolder>() {

    private val daftar = mutableListOf<PeminjamanDto>()

    fun setData(data: List<PeminjamanDto>) {
        daftar.clear()
        daftar.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemPeminjamanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = daftar.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(daftar[position])
    }

    inner class ViewHolder(
        private val binding: ItemPeminjamanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PeminjamanDto) {

            binding.tvFacility.text = item.facilityName
            binding.tvRequester.text =
                "Pemohon: ${item.requesterName}"

            binding.tvDate.text =
                "Tanggal: ${item.bookingDate}"

            binding.tvTime.text =
                "Waktu: ${item.startTime} - ${item.endTime}"

            binding.tvPurpose.text =
                "Keperluan: ${item.purpose}"

            val (statusLabel, textHex, bgHex) = when (item.status) {
                "PENDING" -> Triple("Menunggu", "#986500", "#FFF4DC")
                "APPROVED" -> Triple("Disetujui", "#207A45", "#E4F5EA")
                "REJECTED" -> Triple("Ditolak", "#B73636", "#FDE7E7")
                "RETURNED" -> Triple("Dikembalikan", "#2052CE", "#E8F0FE")
                else -> Triple(item.status, "#555555", "#EEEEEE")
            }
            binding.tvStatus.text = statusLabel
            binding.tvStatus.setTextColor(Color.parseColor(textHex))
            binding.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgHex))

            val showRejection = item.status == "REJECTED" && !item.rejectionReason.isNullOrBlank()
            binding.layoutRejectionReason.visibility = if (showRejection) View.VISIBLE else View.GONE
            binding.tvRejectionReason.text = item.rejectionReason.orEmpty()

            val canProcess = item.status == "PENDING"

            binding.layoutActions.visibility =
                if (canProcess) View.VISIBLE else View.GONE

            binding.btnApprove.setOnClickListener {
                onApprove(item)
            }

            binding.btnReject.setOnClickListener {
                onReject(item)
            }


            // Tombol kembali hanya untuk peminjaman disetujui
            val canReturn = item.status == "APPROVED"

            binding.btnReturn.visibility =
                if (canReturn) View.VISIBLE else View.GONE

            binding.btnReturn.setOnClickListener {
                onReturn(item)
            }

            // Tanggal kembali hanya muncul jika sudah dikembalikan
            binding.tvReturnDate.visibility =
                if (item.status == "RETURNED") {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            binding.tvReturnDate.text =
                "Dikembalikan pada: ${item.returnDate ?: "-"}"

        }
    }
}