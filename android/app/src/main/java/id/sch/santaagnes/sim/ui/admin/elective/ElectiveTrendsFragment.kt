package id.sch.santaagnes.sim.ui.admin.elective

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.ElectivePackageTrendDto
import id.sch.santaagnes.sim.databinding.FragmentElectiveTrendsBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** MPP-06: tren pemilihan paket antar tahun ajaran; data hanya dari backend. */
@AndroidEntryPoint
class ElectiveTrendsFragment : Fragment() {
    private var _binding: FragmentElectiveTrendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ElectiveTrendsViewModel by viewModels()
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentElectiveTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnTrendsBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnTrendsRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnTrendsRetry.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ElectiveTrendsState) {
        binding.progressTrends.isVisible = state.loading
        binding.btnTrendsRefresh.isEnabled = !state.loading
        binding.btnTrendsRetry.isVisible = state.error != null && !state.loading
        binding.tvTrendsError.isVisible = state.error != null
        binding.tvTrendsError.text = state.error
        val result = state.result
        binding.tvTrendsEmpty.isVisible = !state.loading && state.error == null && result?.packages.isNullOrEmpty()
        binding.tvTrendsSummary.isVisible = result != null
        binding.tvTrendsSummary.text = result?.let {
            "${it.packages.size} paket • ${it.academicYears.size} tahun ajaran"
        }.orEmpty()
        binding.trendsList.removeAllViews()
        if (result != null) {
            // Backend mengelompokkan paket berdasarkan nama antar tahun, bukan berdasarkan ID.
            result.packages.forEach { addPackageCard(it) }
        }
    }

    private fun addPackageCard(pkg: ElectivePackageTrendDto) {
        val context = requireContext()
        val card = MaterialCardView(context).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(resources.getColor(R.color.white, context.theme))
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(12)
            layoutParams = params
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        content.addView(label(pkg.packageName, 16f, R.color.agnesian_text, true))
        content.addView(label("Total pemilih: ${format(pkg.totalSelections)}", 13f, R.color.agnesian_blue, false))
        val maxValue = (pkg.byYear.maxOfOrNull { it.selections } ?: 0).coerceAtLeast(1)
        for (year in pkg.byYear) {
            val labelText = "${year.academicYearName}  •  ${format(year.selections)} pemilih"
            content.addView(label(labelText, 12f, R.color.agnesian_text, false).apply {
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.topMargin = dp(10)
                layoutParams = params
            })
            content.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = maxValue
                progress = year.selections
                progressTintList = ColorStateList.valueOf(resources.getColor(R.color.agnesian_button, context.theme))
                progressBackgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.agnesian_tile, context.theme))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
            })
        }
        if (pkg.byYear.isEmpty()) {
            content.addView(label("Belum ada pilihan siswa pada paket ini.", 12f, R.color.agnesian_muted, false))
        }
        card.addView(content)
        binding.trendsList.addView(card)
    }

    private fun label(value: String, size: Float, colorRes: Int, bold: Boolean): TextView = TextView(requireContext()).apply {
        text = value
        textSize = size
        setTextColor(resources.getColor(colorRes, context.theme))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun format(value: Int) = numberFormat.format(value)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
