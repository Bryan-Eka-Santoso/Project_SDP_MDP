package id.sch.santaagnes.sim.ui.task

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.clearErrorOnEdit
import id.sch.santaagnes.sim.databinding.FragmentTeacherTaskFormBinding
import id.sch.santaagnes.sim.domain.model.TeachingAssignment
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

@AndroidEntryPoint
class TeacherTaskFormFragment : Fragment() {

    private var _binding: FragmentTeacherTaskFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeacherTaskFormViewModel by viewModels()
    private val args: TeacherTaskFormFragmentArgs by navArgs()

    private val isEdit get() = args.taskId != 0

    private var dueDate: LocalDate? = null
    private var assignments: List<TeachingAssignment> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeacherTaskFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvHeading.setText(if (isEdit) R.string.task_form_title_edit else R.string.task_form_title_new)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        listOf(binding.layoutAssignment, binding.layoutTitle, binding.layoutDescription, binding.layoutDueDate)
            .forEach { it.clearErrorOnEdit() }

        if (savedInstanceState == null && isEdit) {
            binding.inputTitle.setText(args.title)
            binding.inputDescription.setText(args.description)
            dueDate = args.dueDate?.let(LocalDate::parse)
        }
        savedInstanceState?.getString(STATE_DUE_DATE)?.let { dueDate = LocalDate.parse(it) }
        showDueDate()

        // Kelas + mapel hanya dipilih saat membuat; saat mengubah tampil sebagai label tetap.
        binding.layoutAssignment.isVisible = !isEdit
        binding.tvAssignmentFixed.isVisible = isEdit
        binding.tvAssignmentFixed.text = args.label
        if (!isEdit) viewModel.loadAssignments()

        binding.inputDueDate.setOnClickListener { pickDueDate() }
        binding.btnSave.setOnClickListener { save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.saved.collect { findNavController().popBackStack() } }
            }
        }
    }

    private fun render(state: TeacherTaskFormUiState) {
        binding.progress.isVisible = state.isLoadingAssignments || state.isSaving
        binding.btnSave.isEnabled = !state.isSaving
        binding.layoutAssignment.error = state.assignmentError?.let(::getString)
        binding.layoutTitle.error = state.titleError?.let(::getString)
        binding.layoutDescription.error = state.descriptionError?.let(::getString)
        binding.layoutDueDate.error = state.dueDateError?.let(::getString)

        val noAssignments = !isEdit && !state.isLoadingAssignments && state.assignments.isEmpty()
        val message = state.errorMessage ?: if (noAssignments) getString(R.string.task_no_assignments) else null
        binding.tvError.isVisible = message != null
        binding.tvError.text = message

        if (state.assignments != assignments) {
            assignments = state.assignments
            val labels = assignments.map(::label)
            binding.inputAssignment.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
            )
        }
    }

    private fun label(assignment: TeachingAssignment): String =
        getString(R.string.task_subject_class, assignment.subjectName, assignment.classGroupName)

    private fun save() {
        // Pilihan dikenali dari teks yang tampil, sehingga tetap benar setelah layar diputar.
        val chosen = binding.inputAssignment.text.toString()
        viewModel.save(
            taskId = args.taskId.takeIf { isEdit },
            assignment = assignments.firstOrNull { label(it) == chosen },
            title = binding.inputTitle.text.toString(),
            description = binding.inputDescription.text.toString(),
            dueDate = dueDate
        )
    }

    private fun pickDueDate() {
        val today = DateTimeWib.today()
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.task_due_hint)
            .setSelection((dueDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        // Tugas baru tidak boleh bertenggat sebelum hari ini; mengubah tugas lama dibiarkan bebas.
        if (!isEdit) {
            builder.setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.from(today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
                    .build()
            )
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { millis ->
            dueDate = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            showDueDate()
        }
        picker.show(childFragmentManager, "dueDate")
    }

    private fun showDueDate() {
        binding.inputDueDate.setText(dueDate?.let(DateTimeWib::formatDate).orEmpty())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        dueDate?.let { outState.putString(STATE_DUE_DATE, it.toString()) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_DUE_DATE = "dueDate"
    }
}
