package id.sch.santaagnes.sim.ui.admin.matapelajaran

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.data.remote.MataPelajaranDto
import id.sch.santaagnes.sim.databinding.ItemMataPelajaranBinding

class MataPelajaranAdapter(
    private val onEdit: (MataPelajaranDto) -> Unit,
    private val onDelete: (MataPelajaranDto) -> Unit
) : RecyclerView.Adapter<MataPelajaranAdapter.ViewHolder>() {

    private val daftar = mutableListOf<MataPelajaranDto>()

    fun setData(data: List<MataPelajaranDto>) {
        daftar.clear()
        daftar.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemMataPelajaranBinding.inflate(
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
        private val binding: ItemMataPelajaranBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MataPelajaranDto) {
            binding.tvName.text = item.name
            binding.tvDetail.text = "Kode: ${item.code}"
            binding.tvType.text = item.type

            binding.btnEdit.setOnClickListener {
                onEdit(item)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(item)
            }
        }
    }
}