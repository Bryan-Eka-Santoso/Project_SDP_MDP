package id.sch.santaagnes.sim.ui.task

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentTodoFormBinding
import id.sch.santaagnes.sim.domain.model.SubjectOption
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@AndroidEntryPoint
class TodoFormFragment : Fragment() {

    private var _binding: FragmentTodoFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TodoFormViewModel by viewModels()

    private var dueDate: LocalDate? = null
    private var subjects: List<SubjectOption> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodoFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.getString(STATE_DUE_DATE)?.let { dueDate = LocalDate.parse(it) }
        showDueDate()

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.inputDueDate.setOnClickListener { pickDueDate() }
        binding.btnSave.setOnClickListener { save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.saved.collect { findNavController().popBackStack() } }
            }
        }
    }

    private fun render(state: TodoFormUiState) {
        binding.layoutTitle.error = state.titleError?.let(::getString)
        binding.layoutDueDate.error = state.dueDateError?.let(::getString)
        binding.layoutNotes.error = state.notesError?.let(::getString)

        if (state.subjects != subjects) {
            subjects = state.subjects
            // Pilihan pertama "Tanpa mata pelajaran" karena mapel bersifat opsional.
            val labels = listOf(getString(R.string.todo_subject_none)) + subjects.map { it.name }
            binding.inputSubject.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
            )
        }
    }

    private fun save() {
        // Pilihan dikenali dari teks yang tampil, sehingga tetap benar setelah layar diputar.
        val chosen = binding.inputSubject.text.toString()
        viewModel.save(
            title = binding.inputTitle.text.toString(),
            notes = binding.inputNotes.text.toString(),
            dueDate = dueDate,
            subject = subjects.firstOrNull { it.name == chosen }
        )
    }

    private fun pickDueDate() {
        val today = DateTimeWib.today()
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.task_due_hint)
            .setSelection((dueDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(DateValidatorPointForward.from(todayMillis)).build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            dueDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
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
