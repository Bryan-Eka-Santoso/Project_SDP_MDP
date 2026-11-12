package id.sch.santaagnes.sim.ui.grades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.databinding.FragmentGradeReportBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GradeReportFragment : Fragment() {
    private val viewModel: GradeReportViewModel by viewModels()
    private var _binding: FragmentGradeReportBinding? = null
    private val binding get() = _binding!!
    private var renderedOptions = emptyList<ReportOption>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentGradeReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val role = arguments?.getString("role") ?: "STUDENT"
        binding.tvReportTitle.text = if (role == "TEACHER") "Rekap Nilai Kelas" else "Nilai Siswa"
        binding.btnReloadReport.setOnClickListener {
            val selected = viewModel.state.value.selectedId
            if (selected != null) viewModel.load(selected) else viewModel.start(role)
        }
        binding.spReportSelection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val option = renderedOptions.getOrNull(position) ?: return
                if (viewModel.state.value.selectedId != option.id) viewModel.load(option.id)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressReport.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.tvReportError.text = state.error ?: ""
                    binding.spReportSelection.visibility = if (state.role == "STUDENT") View.GONE else View.VISIBLE
                    if (renderedOptions != state.options) {
                        renderedOptions = state.options
                        binding.spReportSelection.adapter = ArrayAdapter(
                            requireContext(), android.R.layout.simple_spinner_dropdown_item,
                            state.options.map { it.name }
                        )
                    }
                    binding.reportCards.removeAllViews()
                    if (!state.loading && state.error == null && state.entries.isEmpty()) {
                        val empty = TextView(requireContext()).apply { text = "Belum ada data nilai."; textSize = 14f; setPadding(16, 20, 16, 20) }
                        binding.reportCards.addView(empty)
                    }
                    state.entries.forEach { entry ->
                        val text = TextView(requireContext()).apply {
                            this.text = entry
                            textSize = 14f
                            setTextColor(0xFF15243A.toInt())
                            setBackgroundColor(0xFFFFFFFF.toInt())
                            setPadding(20, 18, 20, 18)
                        }
                        val params = android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12 }
                        binding.reportCards.addView(text, params)
                    }
                }
            }
        }
        viewModel.start(role)
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
