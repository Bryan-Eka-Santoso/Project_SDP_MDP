package id.sch.santaagnes.sim.ui.admin.jenispelanggaran

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.JenisPelanggaranDto
import id.sch.santaagnes.sim.databinding.ItemJenisPelanggaranBinding

class JenisPelanggaranAdapter(
    private val onEdit: (JenisPelanggaranDto) -> Unit,
    private val onDelete: (JenisPelanggaranDto) -> Unit
) : RecyclerView.Adapter<JenisPelanggaranAdapter.ViewHolder>() {

    private val daftar = mutableListOf<JenisPelanggaranDto>()

    fun setData(data: List<JenisPelanggaranDto>) {
        daftar.clear()
        daftar.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemJenisPelanggaranBinding.inflate(
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
        private val binding: ItemJenisPelanggaranBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: JenisPelanggaranDto) {
            binding.tvName.text = item.name
            binding.tvPoints.text = "${item.points} Poin"

            binding.btnEdit.setOnClickListener {
                onEdit(item)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(item)
            }
        }
    }
}