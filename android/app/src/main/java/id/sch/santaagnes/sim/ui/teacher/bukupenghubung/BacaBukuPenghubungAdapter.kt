package id.sch.santaagnes.sim.ui.bukupenghubung

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.CatatanBacaBukuDto
import id.sch.santaagnes.sim.databinding.ItemBacaBukuPenghubungBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BacaBukuPenghubungAdapter :
    RecyclerView.Adapter<BacaBukuPenghubungAdapter.ViewHolder>() {

    private val daftar = mutableListOf<CatatanBacaBukuDto>()

    fun setData(data: List<CatatanBacaBukuDto>) {
        daftar.clear()
        daftar.addAll(data)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = daftar.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemBacaBukuPenghubungBinding.inflate(
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

    class ViewHolder(
        private val binding: ItemBacaBukuPenghubungBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CatatanBacaBukuDto) {
            binding.tvDate.text = formatTanggal(item.date)
            binding.tvContent.text = item.content
            binding.tvTeacher.text =
                "Ditulis oleh ${item.teacherName}"
        }

        private fun formatTanggal(value: String): String {
            return try {
                val input = SimpleDateFormat(
                    "yyyy-MM-dd", Locale.US
                ).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                val output = SimpleDateFormat(
                    "dd MMMM yyyy",
                    Locale.forLanguageTag("id-ID")
                ).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                output.format(input.parse(value)!!)
            } catch (e: Exception) {
                value
            }
        }
    }
}
