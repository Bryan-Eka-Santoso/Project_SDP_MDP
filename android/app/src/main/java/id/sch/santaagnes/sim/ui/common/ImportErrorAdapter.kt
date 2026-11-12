package id.sch.santaagnes.sim.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemImportErrorBinding
import id.sch.santaagnes.sim.domain.model.ImportRowError

/** Satu entri per kesalahan impor Excel: nomor baris, nama kolom (bila ada), dan alasannya. */
class ImportErrorAdapter : ListAdapter<ImportRowError, ImportErrorAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemImportErrorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemImportErrorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(error: ImportRowError) {
            val context = binding.root.context
            // Kesalahan tanpa nomor baris menyangkut seluruh berkas (mis. kolom wajib tidak ada).
            binding.tvRow.text = error.row?.let { context.getString(R.string.import_error_row, it) }
                ?: context.getString(R.string.import_error_file)
            binding.tvColumn.isVisible = error.column != null
            binding.tvColumn.text = error.column?.let { context.getString(R.string.import_error_column, it) }
            binding.tvMessage.text = error.message
        }
    }

    private object Diff : DiffUtil.ItemCallback<ImportRowError>() {
        // Daftar kesalahan selalu diganti utuh dan bisa memuat entri kembar, jadi bandingkan isinya.
        override fun areItemsTheSame(oldItem: ImportRowError, newItem: ImportRowError) = oldItem == newItem

        override fun areContentsTheSame(oldItem: ImportRowError, newItem: ImportRowError) = oldItem == newItem
    }
}
