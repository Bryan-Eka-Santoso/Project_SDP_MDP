package id.sch.santaagnes.sim.ui.admin.report

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import id.sch.santaagnes.sim.R
import kotlin.math.min

/** Satu segmen data untuk grafik donat atau batang horizontal laporan (tanpa library eksternal). */
data class ChartSegment(
    val label: String,
    val value: Float,
    val displayValue: String,
    @ColorInt val color: Int
)

/** Satu indikator angka ringkas di atas grafik. */
data class ChartKpi(
    val label: String,
    val value: String,
    @ColorInt val color: Int
)

enum class ReportChartKind { DONUT, HORIZONTAL_BAR }

data class ReportChartSpec(
    val title: String,
    val subtitle: String? = null,
    val kind: ReportChartKind,
    val segments: List<ChartSegment>,
    val centerLabel: String? = null,
    val centerSubLabel: String? = null,
    val kpis: List<ChartKpi> = emptyList()
)

/** Grafik donat custom native Canvas untuk proporsi kategori/status. */
class ReportDonutChartView(context: Context) : View(context) {
    private var segments: List<ChartSegment> = emptyList()
    private var centerText: String = ""
    private var centerSubText: String = ""

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f * density
        strokeCap = Paint.Cap.BUTT
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f * density
        color = ContextCompat.getColor(context, R.color.agnesian_divider)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.agnesian_text)
        textSize = 15f * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.agnesian_muted)
        textSize = 10f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val arcRect = RectF()

    fun setData(items: List<ChartSegment>, center: String?, sub: String?) {
        segments = items.filter { it.value > 0f }
        centerText = center.orEmpty()
        centerSubText = sub.orEmpty()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val pad = ringPaint.strokeWidth / 2f + 4f * density
        val left = (width - size) / 2f + pad
        val top = (height - size) / 2f + pad
        arcRect.set(left, top, left + size - 2f * pad, top + size - 2f * pad)

        canvas.drawOval(arcRect, trackPaint)

        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total > 0f) {
            var startAngle = -90f
            val gap = if (segments.size > 1) 2f else 0f
            for (seg in segments) {
                val sweep = (seg.value / total) * 360f
                if (sweep <= 0f) continue
                ringPaint.color = seg.color
                canvas.drawArc(arcRect, startAngle, (sweep - gap).coerceAtLeast(1f), false, ringPaint)
                startAngle += sweep
            }
        }

        val cx = width / 2f
        val cy = height / 2f
        if (centerText.isNotEmpty()) {
            val offsetY = if (centerSubText.isNotEmpty()) -4f * density else 5f * density
            canvas.drawText(centerText, cx, cy + offsetY, titlePaint)
        }
        if (centerSubText.isNotEmpty()) {
            canvas.drawText(centerSubText, cx, cy + 12f * density, subPaint)
        }
    }
}

/** Membangun kartu grafik ringkas (donat / batang horizontal) beserta KPI & legenda. */
object ReportChartRenderer {

    fun buildCard(context: Context, spec: ReportChartSpec): View {
        val d = context.resources.displayMetrics.density
        val dp = { v: Int -> (v * d).toInt() }

        val card = MaterialCardView(context).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(context, R.color.agnesian_divider)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
                topMargin = dp(4)
                bottomMargin = dp(10)
            }
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        root.addView(
            TextView(context).apply {
                text = spec.title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.agnesian_text))
            }
        )

        spec.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            root.addView(
                TextView(context).apply {
                    text = sub
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.agnesian_muted))
                    setPadding(0, dp(2), 0, 0)
                }
            )
        }

        if (spec.kpis.isNotEmpty()) {
            val kpiRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            }
            spec.kpis.forEachIndexed { index, kpi ->
                val box = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(ContextCompat.getColor(context, R.color.agnesian_surface_soft))
                        setStroke(dp(1), ContextCompat.getColor(context, R.color.agnesian_divider))
                    }
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = dp(6)
                    }
                }
                box.addView(
                    TextView(context).apply {
                        text = kpi.label
                        textSize = 11f
                        setTextColor(ContextCompat.getColor(context, R.color.agnesian_muted))
                    }
                )
                box.addView(
                    TextView(context).apply {
                        text = kpi.value
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(kpi.color)
                        setPadding(0, dp(2), 0, 0)
                    }
                )
                kpiRow.addView(box)
            }
            root.addView(kpiRow)
        }

        when (spec.kind) {
            ReportChartKind.DONUT -> root.addView(buildDonutSection(context, spec, dp))
            ReportChartKind.HORIZONTAL_BAR -> root.addView(buildBarSection(context, spec.segments, dp))
        }

        card.addView(root)
        return card
    }

    private fun buildDonutSection(context: Context, spec: ReportChartSpec, dp: (Int) -> Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        val donut = ReportDonutChartView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(116), dp(116))
            setData(spec.segments, spec.centerLabel, spec.centerSubLabel)
        }
        row.addView(donut)

        val legend = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        spec.segments.forEach { seg ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(seg.color)
                }
            }
            val tvLabel = TextView(context).apply {
                text = seg.label
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.agnesian_text))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvVal = TextView(context).apply {
                text = seg.displayValue
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(seg.color)
            }
            item.addView(dot)
            item.addView(tvLabel)
            item.addView(tvVal)
            legend.addView(item)
        }
        row.addView(legend)
        return row
    }

    private fun buildBarSection(context: Context, segments: List<ChartSegment>, dp: (Int) -> Int): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val maxVal = segments.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f

        segments.take(MAX_BAR_ITEMS).forEach { seg ->
            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(
                TextView(context).apply {
                    text = seg.label
                    textSize = 12f
                    maxLines = 1
                    setTextColor(ContextCompat.getColor(context, R.color.agnesian_text))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            header.addView(
                TextView(context).apply {
                    text = seg.displayValue
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(seg.color)
                    setPadding(dp(8), 0, 0, 0)
                }
            )
            block.addView(header)

            val track = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(8)
                ).apply { topMargin = dp(4) }
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(ContextCompat.getColor(context, R.color.agnesian_divider))
                }
            }
            val ratio = (seg.value / maxVal).coerceIn(0f, 1f)
            if (ratio > 0f) {
                track.addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, ratio)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4).toFloat()
                            setColor(seg.color)
                        }
                    }
                )
            }
            val rest = (1f - ratio).coerceAtLeast(0f)
            if (rest > 0f) {
                track.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, rest)
                })
            }
            block.addView(track)
            container.addView(block)
        }
        return container
    }

    /** Membantu membangun badge status berwarna yang konsisten di kartu laporan. */
    fun buildBadge(context: Context, text: String, @ColorRes bgColorRes: Int): TextView {
        val d = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            background = ContextCompat.getDrawable(context, R.drawable.bg_badge)?.mutate()
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bgColorRes))
            setPadding((10 * d).toInt(), (3 * d).toInt(), (10 * d).toInt(), (3 * d).toInt())
        }
    }

    private const val MAX_BAR_ITEMS = 6
}

/** Adapter header satu item (dipakai via ConcatAdapter) untuk menyisipkan grafik di atas RecyclerView laporan. */
class ReportChartHeaderAdapter : RecyclerView.Adapter<ReportChartHeaderAdapter.ChartHolder>() {
    private var spec: ReportChartSpec? = null

    fun submit(newSpec: ReportChartSpec?) {
        val had = spec != null
        val has = newSpec != null
        spec = newSpec
        when {
            had && has -> notifyItemChanged(0)
            !had && has -> notifyItemInserted(0)
            had && !has -> notifyItemRemoved(0)
        }
    }

    override fun getItemCount(): Int = if (spec != null) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return ChartHolder(container)
    }

    override fun onBindViewHolder(holder: ChartHolder, position: Int) {
        holder.container.removeAllViews()
        spec?.let { holder.container.addView(ReportChartRenderer.buildCard(holder.container.context, it)) }
    }

    class ChartHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
}
