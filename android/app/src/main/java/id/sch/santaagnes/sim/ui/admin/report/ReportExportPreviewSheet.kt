package id.sch.santaagnes.sim.ui.admin.report

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet

/** Data pratinjau tabel Excel yang ditampilkan sebelum berkas `.xlsx` diunduh. */
data class ReportExportPreviewSpec(
    val reportTitle: String,
    val fileName: String,
    val totalCount: Int,
    val filters: List<String>,
    val columns: List<String>,
    val rows: List<List<String>>
)

/** Logika pemformatan pratinjau ekspor yang murni JVM agar mudah diuji lewat unit test. */
internal object ReportExportPreviewFormatter {
    const val MAX_PREVIEW_ROWS = 8

    fun visibleRows(rows: List<List<String>>): List<List<String>> = rows.take(MAX_PREVIEW_ROWS)

    fun remainingCount(totalCount: Int, visibleCount: Int): Int =
        (totalCount - visibleCount).coerceAtLeast(0)

    fun filterSummary(filters: List<String>): String =
        filters.map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifEmpty { "Semua data semester aktif" }

    fun rowCountSummary(totalCount: Int, visibleCount: Int): String = when {
        totalCount <= 0 -> "Belum ada baris data pada filter ini"
        visibleCount < totalCount -> "Menampilkan $visibleCount dari $totalCount baris"
        else -> "Menampilkan seluruh $totalCount baris"
    }
}

/**
 * Membuka bottom sheet pratinjau isi Excel (nama file, filter aktif, total baris, dan tabel sampel)
 * sebelum pengguna memilih lokasi simpan `.xlsx`.
 */
fun Fragment.showReportExportPreview(
    spec: ReportExportPreviewSpec,
    onConfirmExport: () -> Unit
) {
    val context = requireContext()
    val density = resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()

    val blue = ContextCompat.getColor(context, R.color.agnesian_blue)
    val ink = ContextCompat.getColor(context, R.color.agnesian_text)
    val muted = ContextCompat.getColor(context, R.color.agnesian_muted)
    val divider = ContextCompat.getColor(context, R.color.agnesian_divider)
    val softSurface = ContextCompat.getColor(context, R.color.agnesian_surface_soft)
    val white = ContextCompat.getColor(context, R.color.white)
    val paid = ContextCompat.getColor(context, R.color.bill_paid)
    val amber = ContextCompat.getColor(context, R.color.change_substitute)

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val visible = ReportExportPreviewFormatter.visibleRows(spec.rows)
    val effectiveTotal = maxOf(spec.totalCount, spec.rows.size)
    val remaining = ReportExportPreviewFormatter.remainingCount(effectiveTotal, visible.size)

    // Kartu ringkasan berkas & filter aktif
    val summaryBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(softSurface)
            setStroke(dp(1), divider)
        }
    }

    fun addMetaRow(label: String, value: String, valueColor: Int, boldValue: Boolean = true) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        row.addView(
            TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(muted)
                layoutParams = LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
        row.addView(
            TextView(context).apply {
                text = value
                textSize = 12f
                setTextColor(valueColor)
                if (boldValue) setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        summaryBox.addView(row)
    }

    addMetaRow("Berkas", spec.fileName, blue)
    addMetaRow(
        "Total Data",
        if (effectiveTotal > 0) "$effectiveTotal baris siap diekspor" else "0 baris (kosong)",
        if (effectiveTotal > 0) paid else amber
    )
    addMetaRow("Filter", ReportExportPreviewFormatter.filterSummary(spec.filters), ink, boldValue = false)
    root.addView(summaryBox)

    // Judul bagian tabel pratinjau
    root.addView(
        TextView(context).apply {
            text = "Pratinjau Tabel Excel · ${ReportExportPreviewFormatter.rowCountSummary(effectiveTotal, visible.size)}"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(12), 0, dp(6))
        }
    )

    if (visible.isEmpty()) {
        val emptyBox = TextView(context).apply {
            text = "Belum ada data untuk ditampilkan pada filter saat ini.\nAnda tetap dapat mengunduh berkas Excel dengan kolom header."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(14), dp(18), dp(14), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(white)
                setStroke(dp(1), divider)
            }
        }
        root.addView(emptyBox)
    } else {
        val tableContainer = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(white)
                setStroke(dp(1), divider)
            }
        }

        val table = TableLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Baris header biru
        val headerRow = TableRow(context).apply {
            setBackgroundColor(blue)
        }
        spec.columns.forEach { col ->
            headerRow.addView(
                TextView(context).apply {
                    text = col
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(white)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    maxWidth = dp(170)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            )
        }
        table.addView(headerRow)

        // Baris pratinjau data
        visible.forEachIndexed { rowIdx, cells ->
            val tableRow = TableRow(context).apply {
                setBackgroundColor(if (rowIdx % 2 == 0) white else softSurface)
            }
            spec.columns.indices.forEach { colIdx ->
                val value = cells.getOrNull(colIdx).orEmpty().ifBlank { "—" }
                tableRow.addView(
                    TextView(context).apply {
                        text = value
                        textSize = 11.5f
                        setTextColor(if (colIdx == 0) ink else muted)
                        if (colIdx == 0) setTypeface(null, Typeface.BOLD)
                        setPadding(dp(10), dp(7), dp(10), dp(7))
                        maxWidth = dp(180)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                )
            }
            table.addView(tableRow)

            if (rowIdx < visible.lastIndex) {
                table.addView(
                    View(context).apply {
                        layoutParams = TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                        setBackgroundColor(divider)
                    }
                )
            }
        }

        tableContainer.addView(table)
        root.addView(tableContainer)

        root.addView(
            TextView(context).apply {
                text = if (remaining > 0) {
                    "Geser tabel ke samping untuk melihat kolom lain · +$remaining baris lainnya ikut diekspor ke Excel."
                } else {
                    "Geser tabel ke samping untuk melihat seluruh kolom sebelum mengunduh."
                }
                textSize = 11f
                setTextColor(muted)
                setPadding(0, dp(6), 0, dp(2))
            }
        )
    }

    showAgnesianFormSheet(
        context = context,
        title = "Pratinjau Ekspor Excel",
        subtitle = spec.reportTitle,
        contentView = root,
        primaryLabel = "Unduh Excel (.xlsx)",
        secondaryLabel = "Batal"
    ) {
        onConfirmExport()
        true
    }
}
