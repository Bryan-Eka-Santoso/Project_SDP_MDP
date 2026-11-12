package id.sch.santaagnes.sim.ui.admin.report

import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView

/** Pilihan filter laporan pada kolom dropdown: id null = "semua". */
internal class ReportDropdown(
    private val input: MaterialAutoCompleteTextView,
    onPick: (Int?) -> Unit
) {
    private var options: List<Pair<Int?, String>> = emptyList()

    init {
        input.setOnItemClickListener { _, _, position, _ -> onPick(options[position].first) }
    }

    fun render(newOptions: List<Pair<Int?, String>>, selectedId: Int?) {
        if (newOptions != options) {
            options = newOptions
            input.setAdapter(
                ArrayAdapter(input.context, android.R.layout.simple_dropdown_item_1line, newOptions.map { it.second })
            )
        }
        input.setText(options.firstOrNull { it.first == selectedId }?.second, false)
    }
}
