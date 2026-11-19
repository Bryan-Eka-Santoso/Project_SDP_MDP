package id.sch.santaagnes.sim.ui.pelanggaran

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.KejadianPelanggaranDto
import id.sch.santaagnes.sim.databinding.ItemRiwayatPelanggaranBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class RiwayatPelanggaranAdapter :
    RecyclerView.Adapter<RiwayatPelanggaranAdapter.ViewHolder>() {

    private val daftar = mutableListOf<KejadianPelanggaranDto>()

    fun setData(data: List<KejadianPelanggaranDto>) {
        daftar.clear()
        daftar.addAll(data)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = daftar.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val binding = ItemRiwayatPelanggaranBinding.inflate(
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
        private val binding: ItemRiwayatPelanggaranBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: KejadianPelanggaranDto) {

            binding.tvName.text = item.name

            binding.tvPoints.text = "+${item.points} Poin"

            binding.tvReporter.text =
                "Dicatat oleh ${item.reporterName}"

            binding.tvDescription.text =
                item.description?.takeIf { it.isNotBlank() }
                    ?: "Tidak ada keterangan tambahan."

            binding.tvDate.text = try {
                LocalDate.parse(item.date).format(
                    DateTimeFormatter.ofPattern(
                        "dd MMMM yyyy",
                        Locale.forLanguageTag("id-ID")
                    )
                )
            } catch (e: Exception) {
                item.date
            }
        }
    }
}