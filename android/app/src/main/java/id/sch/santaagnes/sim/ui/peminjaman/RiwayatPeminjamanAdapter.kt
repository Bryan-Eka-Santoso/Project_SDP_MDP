package id.sch.santaagnes.sim.ui.peminjaman

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.databinding.ItemRiwayatPeminjamanBinding
import java.text.SimpleDateFormat
import java.util.Locale

class RiwayatPeminjamanAdapter :
    RecyclerView.Adapter<RiwayatPeminjamanAdapter.ViewHolder>() {

    private val daftar = mutableListOf<PeminjamanDto>()

    fun setData(data: List<PeminjamanDto>) {
        daftar.clear()
        daftar.addAll(data)
        notifyDataSetChanged()
    }

    override fun getItemCount() = daftar.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val binding = ItemRiwayatPeminjamanBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(daftar[position])
    }

    inner class ViewHolder(
        private val binding: ItemRiwayatPeminjamanBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PeminjamanDto) {

            binding.tvFacility.text = item.facilityName

            binding.tvType.text = when (item.facilityType) {
                "ROOM" -> "Ruangan"
                "ITEM" -> "Barang"
                else -> item.facilityType
            }

            binding.tvDate.text =
                "Tanggal: ${formatTanggal(item.bookingDate)}"

            binding.tvTime.text =
                "Waktu: ${item.startTime} - ${item.endTime}"

            binding.tvPurpose.text =
                "Keperluan: ${item.purpose}"

            binding.tvRequester.text =
                "Pemohon: ${item.requesterName}"

            binding.tvVerifier.text =
                "Diproses oleh: ${item.verifierName ?: "-"}"

            val (label, textColor, bgColor) = when (item.status) {
                "PENDING" -> Triple(
                    "Menunggu",
                    "#986500",
                    "#FFF4DC"
                )

                "APPROVED" -> Triple(
                    "Disetujui",
                    "#207A45",
                    "#E4F5EA"
                )

                "REJECTED" -> Triple(
                    "Ditolak",
                    "#B73636",
                    "#FDE7E7"
                )

                "RETURNED" -> Triple(
                    "Dikembalikan",
                    "#2052CE",
                    "#E8F0FE"
                )

                else -> Triple(
                    item.status,
                    "#555555",
                    "#EEEEEE"
                )
            }

            binding.tvStatus.text = label

            binding.tvStatus.setTextColor(
                Color.parseColor(textColor)
            )

            binding.tvStatus.backgroundTintList =
                ColorStateList.valueOf(
                    Color.parseColor(bgColor)
                )

            val isRejected = item.status == "REJECTED"
            binding.layoutRejectionReason.visibility =
                if (isRejected) android.view.View.VISIBLE else android.view.View.GONE
            if (isRejected) {
                binding.tvRejectionReason.text =
                    item.rejectionReason?.takeIf { it.isNotBlank() }
                        ?: "Ditolak oleh Admin tanpa catatan tambahan."
            }
        }

        private fun formatTanggal(value: String): String {
            return try {
                val input = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                )

                val output = SimpleDateFormat(
                    "dd MMMM yyyy",
                    Locale.forLanguageTag("id-ID")
                )

                output.format(input.parse(value)!!)
            } catch (e: Exception) {
                value
            }
        }
    }
}
