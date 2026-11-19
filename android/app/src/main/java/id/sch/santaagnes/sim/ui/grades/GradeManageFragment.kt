package id.sch.santaagnes.sim.ui.grades

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.app.AlertDialog
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private var lastDataSignature = ""
    private var currentPanel = "list"
    private val assignmentId by lazy { requireArguments().getInt("assignmentId") }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _binding = FragmentGradeManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val assignmentLabel = arguments?.getString("assignmentLabel") ?: "Mata pelajaran"
        binding.header.tvSubheading.text = assignmentLabel
        binding.tvComponentAssignment.text = assignmentLabel
        binding.header.btnBack.setOnClickListener {
            if (currentPanel == "list") findNavController().navigateUp()
            else showPanel("list")
        }
        binding.btnReload.setOnClickListener { viewModel.load(assignmentId) }
        binding.btnShowComponents.setOnClickListener { showPanel("components") }
        binding.btnBackToGrades.setOnClickListener { showPanel("list") }
        binding.btnAddComponent.setOnClickListener {
            binding.etComponentName.isEnabled = true
            if (uiState.components.size >= 5) {
                Toast.makeText(requireContext(), "Hanya satu komponen Lainnya yang diperbolehkan.", Toast.LENGTH_LONG).show()
            } else {
                activeComponentId = null
                binding.etComponentName.text?.clear()
                binding.etWeight.text?.clear()
                binding.etComponentName.requestFocus()
            }
        }
        binding.btnSaveComponent.setOnClickListener {
            viewModel.saveComponent(
                activeComponentId,
                binding.etComponentName.text.toString(),
                binding.etWeight.text.toString()
            )
        }
        binding.btnCancelEdit.setOnClickListener { showPanel("list") }
        binding.btnSaveScores.setOnClickListener {
            activeStudentId?.let { studentId ->
                viewModel.saveScores(studentId, scoreInputs.mapValues { it.value.text.toString() })
            }
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderStudents()
            }
            override fun afterTextChanged(s: Editable?) = Unit
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
                    val signature = "${state.students}|${state.components}|${state.scores}|${state.assessments}"
                    if (signature != lastDataSignature) {
                        lastDataSignature = signature
                        renderStudents()
                        renderComponents()
                        activeStudentId?.let { renderEdit(it) }
                    }
                    if (state.message == "Nilai berhasil disimpan." &&
                        previous.message != state.message) showPanel("list")
                }
            }
        }
    }

    private fun showPanel(panel: String) {
        currentPanel = panel
        binding.listPanel.visibility = if (panel == "list") View.VISIBLE else View.GONE
        binding.editPanel.visibility = if (panel == "edit") View.VISIBLE else View.GONE
        binding.componentsPanel.visibility = if (panel == "components") View.VISIBLE else View.GONE
        binding.header.tvTitle.text = when (panel) {
            "edit" -> "Ubah Nilai Siswa"
            "components" -> "Pengelolaan Nilai"
            else -> "Nilai Siswa"
        }
    }

    private fun studentValues(studentId: Int): List<Double?> = uiState.components.map { component ->
        val assessments = uiState.assessments.filter { it.gradeComponentId == component.id }
        if (assessments.isNotEmpty() && assessments.any { a -> a.scores.none { it.studentId == studentId } }) {
            null // Ada Tugas/Ulangan yang belum dinilai: nilai komponen belum lengkap.
        } else {
            uiState.scores.find { it.studentId == studentId && it.componentId == component.id }?.value
        }
    }

    private fun weightedFinal(values: List<Double?>): Double? {
        if (uiState.components.isEmpty() || uiState.totalWeight != 100 ||
            values.size != uiState.components.size || values.any { it == null }) return null
        return uiState.components.indices.sumOf { i ->
            values[i]!! * uiState.components[i].weight
        } / 100.0
    }

    private fun formatNumber(value: Double?): String =
        value?.let { String.format(Locale("id", "ID"), "%.2f", it) } ?: "—"

    private fun renderStudents() {
        if (_binding == null) return
        binding.studentsContainer.removeAllViews()
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        val filtered = uiState.students.filter {
            it.name.contains(query, ignoreCase = true) || it.nis?.contains(query) == true
        }
        binding.tvEmpty.text = if (filtered.isEmpty() && !uiState.isLoading) {
            "Tidak ada siswa untuk ditampilkan."
        } else ""
        val complete = uiState.students.mapNotNull { weightedFinal(studentValues(it.id)) }
        val average = if (complete.isNotEmpty()) formatNumber(complete.average()) else "—"
        val total = uiState.students.size
        val completeCount = complete.size
        binding.tvStats.text = "Total siswa: $total     Lengkap: $completeCount/$total     Belum: ${total - completeCount}\nRata-rata: $average"

        filtered.forEach { student ->
            val row = layoutInflater.inflate(
                R.layout.item_grade_student, binding.studentsContainer, false
            )
            row.findViewById<TextView>(R.id.tvStudentName).text = student.name
            row.findViewById<TextView>(R.id.btnEditScore).setOnClickListener {
                activeStudentId = student.id
                renderEdit(student.id)
                showPanel("edit")
            }
            val values = studentValues(student.id)
            val cellContainer = row.findViewById<LinearLayout>(R.id.scoreCellsContainer)
            // Figma Bryan: angka berwarna biru di atas label, empat kolom per baris.
            val items = uiState.components.mapIndexed { index, component ->
                component.name to formatNumber(values[index])
            } + ("Akhir" to formatNumber(weightedFinal(values)))
            items.chunked(4).forEach { chunk ->
                val line = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                line.setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
                chunk.forEach { (label, value) ->
                    val cell = layoutInflater.inflate(
                        R.layout.item_grade_score_cell, line, false
                    )
                    cell.findViewById<TextView>(R.id.tvValue).text = value
                    cell.findViewById<TextView>(R.id.tvLabel).text = label
                    line.addView(cell)
                }
                // Isi baris terakhir dengan spacer agar ukuran tiap kolom konsisten.
                repeat(4 - chunk.size) {
                    line.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
                }
                cellContainer.addView(line)
            }
            if (weightedFinal(values) == null) {
                row.findViewById<TextView>(R.id.tvFinalStatus).apply {
                    visibility = View.VISIBLE
                    text = "Nilai belum lengkap"
                }
            }
            binding.studentsContainer.addView(row)
        }
    }

    private fun renderEdit(studentId: Int) {
        val student: GradeStudent = uiState.students.find { it.id == studentId } ?: return
        val values = studentValues(studentId)
        binding.tvEditStudent.text = "${student.name} (${student.nis.orEmpty()})"
        binding.tvEditSummary.text = uiState.components.mapIndexed { i, component ->
            "${component.name}: ${formatNumber(values[i])}"
        }.joinToString("    •    ")
        binding.tvEditFinal.text = "Nilai Akhir: ${formatNumber(weightedFinal(values))}"
        binding.editFieldsContainer.removeAllViews()
        scoreInputs.clear()
        uiState.components.forEach { component ->
            if (isRepeatedComponent(component.name)) {
                renderAssessmentGroup(studentId, component.id, component.name, component.weight)
                return@forEach
            }
            val field = layoutInflater.inflate(
                R.layout.item_grade_input, binding.editFieldsContainer, false
            )
            field.findViewById<TextView>(R.id.tvInputLabel).text =
                "${component.name} (${component.weight}%)"
            val input = field.findViewById<EditText>(R.id.etGradeInput)
            val previous = uiState.scores.find {
                it.studentId == studentId && it.componentId == component.id
            }
            input.setText(previous?.value?.toString().orEmpty())
            scoreInputs[component.id] = input
            binding.editFieldsContainer.addView(field)
        }
    }

    private fun isRepeatedComponent(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.ROOT)
        return normalized == "tugas" || normalized == "ulangan" || normalized == "ulangan harian"
    }

    /** Input detail Tugas/Ulangan milik satu kelas, nilai per siswa. */
    private fun renderAssessmentGroup(studentId: Int, componentId: Int, name: String, weight: Int) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (10 * resources.displayMetrics.density).toInt()
            setPadding(0, pad, 0, pad)
        }
        val heading = TextView(requireContext()).apply {
            text = "$name ($weight%) — nilai rata-rata otomatis"
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(30, 80, 180))
        }
        container.addView(heading)
        val assessments = uiState.assessments.filter { it.gradeComponentId == componentId }.sortedBy { it.sequence }
        if (assessments.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "Belum ada $name. Buat penilaian pertama untuk seluruh kelas."
                textSize = 12f
            })
        } else {
            assessments.forEach { assessment ->
                val saved = assessment.scores.find { it.studentId == studentId }?.value
                val line = Button(requireContext()).apply {
                    isAllCaps = false
                    text = "${assessment.title}     ${saved?.let { formatNumber(it) } ?: "Belum diisi"}     • Isi Nilai"
                    setOnClickListener { showAssessmentScoreDialog(assessment.id, studentId, assessment.title, saved) }
                }
                container.addView(line, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            val filled = assessments.mapNotNull { a -> a.scores.find { it.studentId == studentId }?.value }
            val averageLabel = TextView(requireContext()).apply {
                text = if (filled.size == assessments.size)
                    "Rata-rata $name: ${formatNumber(filled.average())} (${filled.size}/${assessments.size} terisi)"
                else "Nilai $name belum lengkap: ${filled.size}/${assessments.size} terisi"
                textSize = 12f
            }
            container.addView(averageLabel)
        }
        container.addView(Button(requireContext()).apply {
            isAllCaps = false
            text = "+ Tambah $name untuk seluruh kelas"
            setOnClickListener { showCreateAssessmentDialog(componentId, name, assessments.size + 1) }
        })
        binding.editFieldsContainer.addView(container)
    }

    private fun showCreateAssessmentDialog(componentId: Int, name: String, next: Int) {
        val input = EditText(requireContext()).apply {
            setSingleLine(true)
            setText("$name $next")
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Tambah $name")
            .setMessage("Penilaian ini dibuat untuk semua siswa pada kelas dan mata pelajaran yang sama.")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Tambah") { _, _ -> viewModel.createAssessment(componentId, input.text.toString()) }
            .show()
    }

    private fun showAssessmentScoreDialog(assessmentId: Int, studentId: Int, title: String, current: Double?) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Nilai 0 - 100"
            current?.let { setText(it.toString()) }
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("Masukkan nilai untuk siswa ini. Rata-rata dihitung setelah semua penilaian terisi.")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                viewModel.saveAssessmentScore(assessmentId, studentId, input.text.toString())
            }.show()
    }

    private fun renderComponents() {
        if (_binding == null) return
        val hasExtra = uiState.components.size >= 5
        binding.btnAddComponent.isEnabled = !hasExtra
        binding.btnAddComponent.text = if (hasExtra) "Komponen Lainnya sudah ditambahkan (maks. 1)" else "+ Tambah Komponen Lainnya"
        binding.componentsContainer.removeAllViews()
        uiState.components.forEach { component ->
            val row = layoutInflater.inflate(
                R.layout.item_grade_component, binding.componentsContainer, false
            )
            row.findViewById<TextView>(R.id.tvComponentLabel).text =
                "${component.name}    •    ${component.weight}%"
            row.findViewById<TextView>(R.id.btnEditComponent).setOnClickListener {
                activeComponentId = component.id
                binding.etComponentName.setText(component.name)
                binding.etWeight.setText(component.weight.toString())
                binding.etComponentName.isEnabled = !isStandardComponent(component.name)
            }
            binding.componentsContainer.addView(row)
        }
    }

    private fun isStandardComponent(name: String): Boolean =
        name.trim().lowercase(Locale.ROOT) in setOf("tugas", "ulangan", "uts", "uas")

    override fun onDestroyView() {
        scoreInputs.clear()
        _binding = null
        super.onDestroyView()
    }
}
