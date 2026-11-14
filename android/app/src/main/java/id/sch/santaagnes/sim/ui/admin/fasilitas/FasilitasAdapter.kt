package id.sch.santaagnes.sim.ui.admin.fasilitas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.FasilitasDto
import id.sch.santaagnes.sim.databinding.ItemFasilitasBinding

class FasilitasAdapter(
    private val onEdit: (FasilitasDto) -> Unit,
    private val onDelete: (FasilitasDto) -> Unit
) : RecyclerView.Adapter<FasilitasAdapter.ViewHolder>() {

    private val daftar = mutableListOf<FasilitasDto>()

    fun setData(data: List<FasilitasDto>) {
        daftar.clear()
        daftar.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemFasilitasBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun getItemCount() = daftar.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(daftar[position])
    }

    inner class ViewHolder(
        private val binding: ItemFasilitasBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FasilitasDto) {

            binding.tvName.text = item.name

            binding.tvType.text =
                if (item.type == "ROOM") "Ruangan" else "Barang"

            binding.tvQuantity.text =
                "Jumlah: ${item.quantity}"

            binding.btnEdit.setOnClickListener {
                onEdit(item)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(item)
            }
        }
    }
}