package id.sch.santaagnes.sim.ui.progress

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.remote.ProgressNoteDto
import id.sch.santaagnes.sim.databinding.FragmentTeacherProgressNotesBinding
import kotlinx.coroutines.launch
import java.time.LocalDate

@AndroidEntryPoint
class TeacherProgressNotesFragment: Fragment() {
    private var _binding: FragmentTeacherProgressNotesBinding? = null
    private val binding get() = _binding!!
    private val vm: TeacherProgressNotesViewModel by viewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeacherProgressNotesBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val assignmentId = requireArguments().getInt("assignmentId")
        binding.btnNoteBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnNotesRetry.setOnClickListener { vm.refresh() }
        binding.btnNotesMore.setOnClickListener { vm.more() }
        binding.btnNoteAdd.setOnClickListener { showEditor(null) }
        vm.start(assignmentId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    binding.progressNotes.isVisible = state.loading || state.saving
                    binding.btnNoteAdd.isEnabled = !state.saving && state.students.isNotEmpty()
                    binding.btnNotesRetry.isVisible = state.error != null
                    binding.btnNotesMore.isVisible = state.more
                    binding.tvNotesStatus.text = state.error ?: when {
                        state.loading && state.items.isEmpty() -> "Memuat catatan..."
                        state.items.isEmpty() -> "Belum ada catatan perkembangan untuk kelas ini."
                        else -> "${state.total} catatan perkembangan"
                    }
                    binding.listNotes.removeAllViews()
                    state.items.forEach { note -> addProgressNoteCard(binding.listNotes, note, true) { showEditor(note) } }
                }
            }
        }
    }
    private fun showEditor(note: ProgressNoteDto?) {
        val students = vm.state.value.students
        if (students.isEmpty()) return
        val density = resources.displayMetrics.density
        fun dp(i: Int) = (i * density).toInt()
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val spinner = Spinner(requireContext())
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            students.map { "${it.name} (${it.nis ?: "-"})" })
        if (note != null) { spinner.setSelection(students.indexOfFirst { it.id == note.studentId }.coerceAtLeast(0)); spinner.isEnabled = false }
        layout.addView(spinner)
        var selectedDate = runCatching { LocalDate.parse(note?.date ?: "") }.getOrDefault(DateTimeWib.today())
        val dateButton = android.widget.Button(requireContext()).apply { text = selectedDate.toString() }
        dateButton.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate = LocalDate.of(y, m + 1, d)
                dateButton.text = selectedDate.toString()
            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }
        layout.addView(dateButton)
        val content = EditText(requireContext()).apply {
            hint = "Catatan perkembangan (maks. 500 karakter)"
            minLines = 3; maxLines = 7
            setText(note?.content.orEmpty())
            filters = arrayOf(android.text.InputFilter.LengthFilter(500))
        }
        layout.addView(content)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (note == null) "Tambah Catatan" else "Edit Catatan")
            .setView(layout)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = content.text.toString().trim()
                if (value.isEmpty()) { content.error = "Catatan wajib diisi"; return@setOnClickListener }
                vm.save(students[spinner.selectedItemPosition].id, note, selectedDate.toString(), value)
                dialog.dismiss()
            }
        }
        dialog.show()
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
