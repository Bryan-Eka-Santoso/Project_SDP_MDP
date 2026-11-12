package id.sch.santaagnes.sim.ui.grades

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentGradeManageBinding
import id.sch.santaagnes.sim.domain.model.GradeStudent
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class GradeManageFragment : Fragment() {
    private var _binding: FragmentGradeManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GradeManageViewModel by viewModels()
    private var uiState = GradeManageUiState()
    private var activeStudentId: Int? = null
    private var activeComponentId: Int? = null
    private val scoreInputs = linkedMapOf<Int, EditText>()
    private var lastDataSignature: String = ""
    private val assignmentId: Int by lazy { requireArguments().getInt("assignmentId") }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentGradeManageBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvSubheading.text = arguments?.getString("assignmentLabel") ?: "Kelas dan mata pelajaran"
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnReload.setOnClickListener { viewModel.load(assignmentId) }
        binding.btnShowComponents.setOnClickListener { showPanel("components") }
        binding.btnBackToGrades.setOnClickListener { showPanel("list") }
        binding.btnAddComponent.setOnClickListener { activeComponentId = null; binding.etComponentName.text?.clear(); binding.etWeight.text?.clear() }
        binding.btnSaveComponent.setOnClickListener {
            viewModel.saveComponent(activeComponentId, binding.etComponentName.text.toString(), binding.etWeight.text.toString())
        }
        binding.btnCancelEdit.setOnClickListener { showPanel("list") }
        binding.btnSaveScores.setOnClickListener {
            activeStudentId?.let { studentId -> viewModel.saveScores(studentId, scoreInputs.mapValues { it.value.text.toString() }) }
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderStudents() }
            override fun afterTextChanged(s: Editable?) {}
        })
        showPanel("list")
        viewModel.load(assignmentId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val previous = uiState
                    uiState = state
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.tvError.text = state.errorMessage.orEmpty()
                    binding.tvMessage.text = state.message.orEmpty()
                    binding.tvWeight.text = "Total bobot: ${state.totalWeight}% / 100%"
                    binding.btnSaveScores.isEnabled = !state.isLoading && state.totalWeight == 100
                    binding.btnSaveComponent.isEnabled = !state.isLoading
                    val signature = "${state.students}|${state.components}|${state.scores}"
                    if (signature != lastDataSignature) {
                        lastDataSignature = signature
                        renderStudents()
                        renderComponents()
                        // Jangan timpa form ketika sedang mengetik. Hanya render ulang jika data server berubah.
                        if (activeStudentId != null) renderEdit(activeStudentId!!)
                    }
                    // Kembali ke daftar setelah data nilai baru berhasil tersimpan.
                    if (state.message == "Nilai berhasil disimpan." && previous.message != state.message) showPanel("list")
                }
            }
        }
    }

    private fun showPanel(panel: String) {
        binding.listPanel.visibility = if (panel == "list") View.VISIBLE else View.GONE
        binding.editPanel.visibility = if (panel == "edit") View.VISIBLE else View.GONE
        binding.componentsPanel.visibility = if (panel == "components") View.VISIBLE else View.GONE
        binding.tvTitle.text = when (panel) {
            "edit" -> "Ubah Nilai Siswa"
            "components" -> "Komponen Penilaian"
            else -> "Nilai Siswa"
        }
    }

    private fun renderStudents() {
        if (_binding == null) return
        binding.studentsContainer.removeAllViews()
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        val students = uiState.students.filter { it.name.contains(query, ignoreCase = true) || (it.nis?.contains(query) == true) }
        binding.tvEmpty.text = if (students.isEmpty() && !uiState.isLoading) "Tidak ada siswa untuk ditampilkan." else ""
        for (student in students) {
            val row = layoutInflater.inflate(R.layout.item_grade_student, binding.studentsContainer, false)
            row.findViewById<TextView>(R.id.tvStudentName).text = student.name
            row.findViewById<TextView>(R.id.tvStudentScores).text = scoreSummary(student)
            row.findViewById<Button>(R.id.btnEditScore).setOnClickListener {
                activeStudentId = student.id
                renderEdit(student.id)
                showPanel("edit")
            }
            binding.studentsContainer.addView(row)
        }
    }

    private fun scoreSummary(student: GradeStudent): String {
        val values = uiState.components.map { component ->
            uiState.scores.find { it.studentId == student.id && it.componentId == component.id }?.value
        }
        val pieces = uiState.components.mapIndexed { index, component ->
            "${component.name}: ${values[index]?.let { String.format(Locale("id", "ID"), "%.2f", it) } ?: "—"}"
        }
        val final = if (uiState.components.isNotEmpty() && values.all { it != null } && uiState.totalWeight == 100) {
            val weighted = uiState.components.indices.sumOf { i -> values[i]!! * uiState.components[i].weight } / 100.0
            "Nilai akhir: ${String.format(Locale("id", "ID"), "%.2f", weighted)}"
        } else "Nilai akhir: Belum lengkap"
        return (pieces + final).joinToString("   •   ")
    }

    private fun renderEdit(studentId: Int) {
        val student = uiState.students.find { it.id == studentId } ?: return
        binding.tvEditStudent.text = student.name
        binding.editFieldsContainer.removeAllViews()
        scoreInputs.clear()
        uiState.components.forEach { component ->
            val row = layoutInflater.inflate(R.layout.item_grade_input, binding.editFieldsContainer, false)
            row.findViewById<TextView>(R.id.tvInputLabel).text = "${component.name} (${component.weight}%)"
            val input = row.findViewById<EditText>(R.id.etGradeInput)
            val old = uiState.scores.find { it.studentId == studentId && it.componentId == component.id }
            input.setText(old?.value?.toString().orEmpty())
            scoreInputs[component.id] = input
            binding.editFieldsContainer.addView(row)
        }
    }

    private fun renderComponents() {
        binding.componentsContainer.removeAllViews()
        uiState.components.forEach { component ->
            val row = Button(requireContext()).apply {
                text = "${component.name} — ${component.weight}% (ubah)"
                isAllCaps = false
                setOnClickListener {
                    activeComponentId = component.id
                    binding.etComponentName.setText(component.name)
                    binding.etWeight.setText(component.weight.toString())
                }
            }
            binding.componentsContainer.addView(row)
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
