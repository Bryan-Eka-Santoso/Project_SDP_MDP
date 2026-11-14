package id.sch.santaagnes.sim.ui.admin.peminjaman

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.databinding.ItemPeminjamanBinding

class PeminjamanAdapter(
    private val onApprove: (PeminjamanDto) -> Unit,
    private val onReject: (PeminjamanDto) -> Unit
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

            binding.tvStatus.text = when (item.status) {
                "PENDING" -> "Menunggu Persetujuan"
                "APPROVED" -> "Disetujui"
                "REJECTED" -> "Ditolak"
                "RETURNED" -> "Dikembalikan"
                else -> item.status
            }

            val canProcess = item.status == "PENDING"

            binding.layoutActions.visibility =
                if (canProcess) View.VISIBLE else View.GONE

            binding.btnApprove.setOnClickListener {
                onApprove(item)
            }

            binding.btnReject.setOnClickListener {
                onReject(item)
            }
        }
    }
}