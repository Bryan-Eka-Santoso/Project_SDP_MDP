package id.sch.santaagnes.sim.ui.attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentTeacherAttendanceRecapBinding
import id.sch.santaagnes.sim.databinding.ItemAttendanceRecapBinding
import id.sch.santaagnes.sim.domain.model.GradeAssignment
import kotlinx.coroutines.launch
import java.time.LocalDate

@AndroidEntryPoint
class TeacherAttendanceRecapFragment : Fragment() {
    private var _binding: FragmentTeacherAttendanceRecapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TeacherAttendanceRecapViewModel by viewModels()
    private var shownAssignments: List<GradeAssignment>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeacherAttendanceRecapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRecapBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnRecapRetry.setOnClickListener { viewModel.load() }
        binding.btnRecapStart.setOnClickListener {
            pickDate(viewModel.state.value.startDate) { viewModel.setPeriod(it, viewModel.state.value.endDate) }
        }
        binding.btnRecapEnd.setOnClickListener {
            pickDate(viewModel.state.value.endDate) { viewModel.setPeriod(viewModel.state.value.startDate, it) }
        }
        binding.btnRecapAllDates.setOnClickListener { viewModel.setPeriod(null, null) }
        binding.spinnerRecapAssignment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                shownAssignments?.getOrNull(position)?.let { viewModel.select(it.id) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
        viewModel.start(
            arguments?.getInt("assignmentId") ?: 0,
            arguments?.getBoolean("homeroomOnly") ?: false
        )
    }

    private fun render(state: AttendanceRecapState) {
        binding.progressRecap.isVisible = state.loading
        if (shownAssignments != state.assignments) {
            shownAssignments = state.assignments
            binding.spinnerRecapAssignment.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                state.assignments.map { item ->
                    if (item.isOwn || item.teacherName == null) item.label else "${item.label} — ${item.teacherName}"
                }
            )
            val index = state.assignments.indexOfFirst { it.id == state.selectedId }
            if (index >= 0) binding.spinnerRecapAssignment.setSelection(index)
        }
        binding.btnRecapStart.text = state.startDate?.let(::formatDate) ?: getString(R.string.attendance_recap_start)
        binding.btnRecapEnd.text = state.endDate?.let(::formatDate) ?: getString(R.string.attendance_recap_end)
        binding.btnRecapAllDates.isVisible = state.startDate != null || state.endDate != null
        binding.btnRecapRetry.isVisible = state.error != null

        val message = state.error ?: when {
            state.loading -> null
            state.assignments.isEmpty() -> getString(R.string.attendance_recap_no_class)
            state.rows.isEmpty() -> getString(R.string.attendance_recap_no_student)
            else -> null
        }
        binding.tvRecapStatus.isVisible = message != null
        binding.tvRecapStatus.text = message

        binding.listRecap.removeAllViews()
        state.rows.forEach { row ->
            val card = ItemAttendanceRecapBinding.inflate(layoutInflater, binding.listRecap, false)
            card.tvRecapName.text = getString(R.string.attendance_recap_student, row.name, row.nis)
            card.tvRecapCounts.text = getString(
                R.string.attendance_recap_counts, row.present, row.sick, row.excused, row.absent
            )
            binding.listRecap.addView(card.root)
        }
    }

    private fun formatDate(value: String): String =
        runCatching { DateTimeWib.formatDate(LocalDate.parse(value)) }.getOrDefault(value)

    private fun pickDate(current: String?, onPicked: (String) -> Unit) {
        val initial = runCatching { LocalDate.parse(current.orEmpty()) }.getOrDefault(DateTimeWib.today())
        DatePickerDialog(requireContext(), { _, year, month, day ->
            onPicked(LocalDate.of(year, month + 1, day).toString())
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    override fun onDestroyView() {
        shownAssignments = null
        _binding = null
        super.onDestroyView()
    }
}
