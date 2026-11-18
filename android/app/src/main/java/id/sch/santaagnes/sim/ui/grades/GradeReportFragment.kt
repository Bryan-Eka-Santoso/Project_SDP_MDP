package id.sch.santaagnes.sim.ui.grades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentGradeReportBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GradeReportFragment : Fragment() {
    private val viewModel: GradeReportViewModel by viewModels()
    private var _binding: FragmentGradeReportBinding? = null
    private val binding get() = _binding!!
    private var renderedOptions = emptyList<ReportOption>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGradeReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val role = arguments?.getString("role") ?: "STUDENT"
        binding.tvReportTitle.text = if (role == "TEACHER") "Rekap Nilai Kelas" else "Nilai Siswa"
        binding.btnReportBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnReloadReport.setOnClickListener {
            val selected = viewModel.state.value.selectedId
            if (selected != null) viewModel.load(selected) else viewModel.start(role)
        }
        binding.spReportSelection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val option = renderedOptions.getOrNull(position) ?: return
                if (viewModel.state.value.selectedId != option.id) viewModel.load(option.id)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressReport.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.tvReportError.text = state.error.orEmpty()
                    val selectionVisible = state.role != "STUDENT"
                    binding.spReportSelection.visibility = if (selectionVisible) View.VISIBLE else View.GONE
                    binding.tvReportSelectionLabel.visibility = if (selectionVisible) View.VISIBLE else View.GONE
                    if (renderedOptions != state.options) {
                        renderedOptions = state.options
                        binding.spReportSelection.adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_dropdown_item,
                            state.options.map { it.name }
                        )
                    }
                    // Ringkasan dihitung dari seluruh siswa pada hasil rekap (bukan dari filter UI).
                    val isTeacher = state.role == "TEACHER"
                    val total = state.entries.size
                    val complete = state.entries.count { it.isComplete }
                    binding.tvReportClassStats.apply {
                        visibility = if (isTeacher && !state.loading && state.error == null) View.VISIBLE else View.GONE
                        text = "Total siswa: $total     Lengkap: $complete/$total     Belum: ${total - complete}"
                    }
                    binding.reportCards.removeAllViews()
                    if (!state.loading && state.error == null && state.entries.isEmpty()) {
                        binding.reportCards.addView(TextView(requireContext()).apply {
                            text = "Belum ada data nilai."
                            textSize = 13f
                            setPadding(16, 24, 16, 24)
                        })
                    }
                    state.entries.forEach { entry ->
                        val card = layoutInflater.inflate(
                            R.layout.item_grade_report_student,
                            binding.reportCards,
                            false
                        )
                        card.findViewById<TextView>(R.id.tvReportStudentName).text = entry.title
                        card.findViewById<TextView>(R.id.tvReportStudentNis).apply {
                            text = entry.subtitle
                            visibility = if (entry.subtitle.isBlank()) View.GONE else View.VISIBLE
                        }
                        val componentContainer = card.findViewById<LinearLayout>(
                            R.id.reportComponentsContainer
                        )
                        // Format sama dengan halaman Nilai Siswa: angka biru di atas label.
                        val values = entry.components.map { it.name to it.value } +
                            ("Akhir" to if (entry.isComplete) entry.finalScore else "—")
                        values.chunked(4).forEach { chunk ->
                            val row = LinearLayout(requireContext()).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
                            }
                            chunk.forEach { (label, value) ->
                                val cell = layoutInflater.inflate(
                                    R.layout.item_grade_score_cell, row, false
                                )
                                cell.findViewById<TextView>(R.id.tvValue).text =
                                    if (value == "-" || value == "Belum lengkap") "—" else value
                                cell.findViewById<TextView>(R.id.tvLabel).text = label
                                row.addView(cell)
                            }
                            repeat(4 - chunk.size) {
                                row.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
                            }
                            componentContainer.addView(row)
                        }
                        // Rincian nilai Tugas/Ulangan untuk Siswa dan Wali Murid saja.
                        // Read-only: tidak ada field input atau request perubahan nilai.
                        if (state.role == "STUDENT" || state.role == "GUARDIAN") {
                            entry.components.filter { it.assessments.isNotEmpty() }.forEach { component ->
                                val toggle = TextView(requireContext()).apply {
                                    text = "Lihat rincian ${component.name}  ▾"
                                    textSize = 12f
                                    setTextColor(resources.getColor(R.color.teacher_blue, null))
                                    setPadding(dp(4), dp(12), dp(4), dp(9))
                                }
                                val details = LinearLayout(requireContext()).apply {
                                    orientation = LinearLayout.VERTICAL
                                    visibility = View.GONE
                                    setPadding(dp(10), dp(2), dp(10), dp(8))
                                }
                                component.assessments.forEach { assessment ->
                                    val detailRow = LinearLayout(requireContext()).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        setPadding(0, dp(5), 0, dp(5))
                                    }
                                    val label = TextView(requireContext()).apply {
                                        text = assessment.title
                                        textSize = 12f
                                        setTextColor(resources.getColor(R.color.teacher_muted, null))
                                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                    }
                                    val value = TextView(requireContext()).apply {
                                        text = if (assessment.value == "-") "Belum dinilai" else assessment.value
                                        textSize = 12f
                                        setTextColor(resources.getColor(R.color.teacher_blue, null))
                                    }
                                    detailRow.addView(label)
                                    detailRow.addView(value)
                                    details.addView(detailRow)
                                }
                                toggle.setOnClickListener {
                                    val opening = details.visibility != View.VISIBLE
                                    details.visibility = if (opening) View.VISIBLE else View.GONE
                                    toggle.text = (if (opening) "Tutup rincian " else "Lihat rincian ") +
                                        component.name + (if (opening) "  ▴" else "  ▾")
                                }
                                componentContainer.addView(toggle)
                                componentContainer.addView(details)
                            }
                        }
                        card.findViewById<TextView>(R.id.tvReportPredicate).text =
                            if (entry.isComplete) "Predikat: ${entry.predicate}" else "Nilai belum lengkap"
                        val params = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
                        binding.reportCards.addView(card, params)
                    }
                }
            }
        }
        viewModel.start(role)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
