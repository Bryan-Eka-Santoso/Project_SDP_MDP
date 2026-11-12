package id.sch.santaagnes.sim.ui.common

import android.view.LayoutInflater
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.IncludeImportBodyBinding
import id.sch.santaagnes.sim.databinding.ItemImportColumnBinding
import id.sch.santaagnes.sim.domain.model.ImportRowError

/** Batas daftar kesalahan dari backend (`MAX_REPORTED_ERRORS`); dipakai hanya untuk catatan "terpotong". */
private const val SERVER_ERROR_LIMIT = 200

/**
 * Mengisi dan merender `include_import_body.xml`, bentuk layar impor Excel yang sama untuk jadwal
 * dan pembayaran. Fragment tetap hanya merender state dan meneruskan aksi ke ViewModel.
 */
class ImportBodyView(
    private val binding: IncludeImportBodyBinding,
    @StringRes info: Int,
    @ArrayRes columns: Int
) {

    private val errorAdapter = ImportErrorAdapter()

    init {
        val context = binding.root.context
        binding.tvInfo.setText(info)
        binding.rvErrors.layoutManager = LinearLayoutManager(context)
        binding.rvErrors.adapter = errorAdapter

        // Setiap entri larik berbentuk "Nama|keterangan".
        val inflater = LayoutInflater.from(context)
        context.resources.getStringArray(columns).forEach { entry ->
            val (name, hint) = entry.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val item = ItemImportColumnBinding.inflate(inflater, binding.columnList, false)
            item.tvColumnName.text = name
            item.tvColumnHint.text = hint
            binding.columnList.addView(item.root)
        }
    }

    fun setOnTemplateClick(action: () -> Unit) = binding.btnTemplate.setOnClickListener { action() }

    fun setOnPickClick(action: () -> Unit) = binding.btnPick.setOnClickListener { action() }

    fun setOnImportClick(action: () -> Unit) = binding.btnImport.setOnClickListener { action() }

    fun render(
        isUploading: Boolean,
        canImport: Boolean,
        fileUri: String?,
        fileName: String?,
        successText: String?,
        errorMessage: String?,
        importErrors: List<ImportRowError>,
        errorRowCount: Int
    ) {
        val context = binding.root.context
        binding.btnImport.isEnabled = canImport
        binding.btnPick.isEnabled = !isUploading
        binding.btnTemplate.isEnabled = !isUploading

        binding.tvFileName.isVisible = fileUri != null
        binding.tvFileName.text = fileName ?: context.getString(R.string.import_file_selected)

        binding.tvSuccess.isVisible = successText != null
        binding.tvSuccess.text = successText

        // Impor ditolak dengan rincian: kartu per baris. Kegagalan lain: banner teks satu blok.
        val detailed = importErrors.isNotEmpty()
        binding.errorCard.isVisible = detailed
        binding.tvError.isVisible = errorMessage != null && !detailed
        binding.tvError.text = errorMessage
        if (detailed) {
            val summary = if (errorRowCount > 0) {
                context.getString(R.string.import_error_summary, importErrors.size, errorRowCount)
            } else {
                context.getString(R.string.import_error_summary_no_row, importErrors.size)
            }
            binding.tvErrorSummary.text = if (importErrors.size >= SERVER_ERROR_LIMIT) {
                summary + " " + context.getString(R.string.import_error_truncated, SERVER_ERROR_LIMIT)
            } else {
                summary
            }
        }
        errorAdapter.submitList(importErrors)
    }
}
