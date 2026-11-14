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
        binding.bottomNav.root.visibility = if (role == "TEACHER") View.VISIBLE else View.GONE
        binding.btnReportBack.setOnClickListener { findNavController().navigateUp() }
        binding.bottomNav.navTeacherHome.setOnClickListener {
            if (!findNavController().popBackStack(R.id.teacherHomeFragment, false)) {
                findNavController().navigate(R.id.teacherHomeFragment)
            }
        }
        binding.bottomNav.navTeacherClasses.setOnClickListener {
            findNavController().navigate(R.id.teacherClassesFragment)
        }
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
                    binding.reportCards.removeAllViews()
                    if (!state.loading && state.error == null && state.entries.isEmpty()) {
                        binding.reportCards.addView(TextView(requireContext()).apply {
                            text = "Belum ada data nilai."
                            textSize = 13f
                            setPadding(16, 24, 16, 24)
                        })
                    }
                    state.entries.forEach { entry ->
                        val card = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(18, 16, 18, 16)
                            setBackgroundResource(R.drawable.bg_teacher_card)
                        }
                        val lines = entry.lines()
                        card.addView(TextView(requireContext()).apply {
                            text = lines.firstOrNull().orEmpty()
                            textSize = 14f
                            setTextColor(0xFF20242C.toInt())
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                        })
                        card.addView(TextView(requireContext()).apply {
                            text = lines.drop(1).joinToString("\n")
                            textSize = 12f
                            setTextColor(0xFF2D65D7.toInt())
                            setPadding(0, 9, 0, 0)
                        })
                        val params = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12 }
                        binding.reportCards.addView(card, params)
                    }
                }
            }
        }
        viewModel.start(role)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
