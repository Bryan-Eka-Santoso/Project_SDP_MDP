package id.sch.santaagnes.sim.ui.admin.schedule

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
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentSlotFormBinding
import id.sch.santaagnes.sim.domain.model.AssignmentOption
import id.sch.santaagnes.sim.domain.model.RoomOption
import id.sch.santaagnes.sim.domain.model.ScheduleLookups
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

@AndroidEntryPoint
class SlotFormFragment : Fragment() {

    private var _binding: FragmentSlotFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SlotFormViewModel by viewModels()
    private val args: SlotFormFragmentArgs by navArgs()

    private val isEdit get() = args.slotId != 0
    private val days: List<DayOfWeek> = DayOfWeek.entries.take(6)

    private var lookups: ScheduleLookups? = null
    private var start: LocalTime? = null
    private var end: LocalTime? = null
    private var needsPrefill = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlotFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvHeading.setText(if (isEdit) R.string.slot_form_title_edit else R.string.slot_form_title_new)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.inputDay.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, days.map(DateTimeWib::dayName))
        )
        needsPrefill = savedInstanceState == null && isEdit
        if (savedInstanceState == null && isEdit) {
            args.dayOfWeek?.let { binding.inputDay.setText(DateTimeWib.dayName(DayOfWeek.valueOf(it)), false) }
            start = args.startTime?.let(LocalTime::parse)
            end = args.endTime?.let(LocalTime::parse)
        }
        savedInstanceState?.getString(STATE_START)?.let { start = LocalTime.parse(it) }
        savedInstanceState?.getString(STATE_END)?.let { end = LocalTime.parse(it) }
        showTimes()

        binding.inputStart.setOnClickListener { pickTime(start, R.string.slot_start_hint) { start = it; showTimes() } }
        binding.inputEnd.setOnClickListener { pickTime(end, R.string.slot_end_hint) { end = it; showTimes() } }
        binding.btnSave.setOnClickListener { save() }
        viewModel.loadLookups()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.saved.collect { findNavController().popBackStack() } }
            }
        }
    }

    private fun render(state: SlotFormUiState) {
        binding.progress.isVisible = state.isLoadingLookups || state.isSaving
        binding.btnSave.isEnabled = !state.isSaving && state.lookups != null
        binding.layoutAssignment.error = state.assignmentError?.let(::getString)
        binding.layoutRoom.error = state.roomError?.let(::getString)
        binding.layoutDay.error = state.dayError?.let(::getString)
        binding.layoutStart.error = state.startError?.let(::getString)
        binding.layoutEnd.error = state.endError?.let { getString(it) }

        val noRooms = state.lookups != null && state.lookups.rooms.isEmpty()
        val message = state.errorMessage ?: if (noRooms) getString(R.string.slot_no_rooms) else null
        binding.tvError.isVisible = message != null
        binding.tvError.text = message

        val loaded = state.lookups
        if (loaded != null && loaded != lookups) {
            lookups = loaded
            binding.inputAssignment.setAdapter(
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    loaded.assignments.map(::label)
                )
            )
            binding.inputRoom.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, loaded.rooms.map { it.name })
            )
            if (needsPrefill) prefill(loaded)
        }
    }

    // Mengubah slot: pilihan awal diambil dari slot yang dibuka (dikirim lewat argumen navigasi).
    private fun prefill(loaded: ScheduleLookups) {
        needsPrefill = false
        loaded.assignments.firstOrNull { it.id == args.assignmentId }
            ?.let { binding.inputAssignment.setText(label(it), false) }
        loaded.rooms.firstOrNull { it.id == args.roomId }
            ?.let { binding.inputRoom.setText(it.name, false) }
    }

    private fun label(assignment: AssignmentOption): String =
        getString(R.string.slot_assignment_label, assignment.classGroupName, assignment.subjectName, assignment.teacherName)

    private fun save() {
        // Pilihan dikenali dari teks yang tampil, sehingga tetap benar setelah layar diputar.
        val assignmentText = binding.inputAssignment.text.toString()
        val roomText = binding.inputRoom.text.toString()
        val dayText = binding.inputDay.text.toString()
        val room: RoomOption? = lookups?.rooms?.firstOrNull { it.name == roomText }
        viewModel.save(
            slotId = args.slotId.takeIf { isEdit },
            assignment = lookups?.assignments?.firstOrNull { label(it) == assignmentText },
            room = room,
            day = days.firstOrNull { DateTimeWib.dayName(it) == dayText },
            start = start,
            end = end
        )
    }

    private fun pickTime(current: LocalTime?, @androidx.annotation.StringRes title: Int, onPicked: (LocalTime) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current?.hour ?: DEFAULT_HOUR)
            .setMinute(current?.minute ?: 0)
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(LocalTime.of(picker.hour, picker.minute)) }
        picker.show(childFragmentManager, "time")
    }

    private fun showTimes() {
        binding.inputStart.setText(start?.let(DateTimeWib::formatTime).orEmpty())
        binding.inputEnd.setText(end?.let(DateTimeWib::formatTime).orEmpty())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        start?.let { outState.putString(STATE_START, it.toString()) }
        end?.let { outState.putString(STATE_END, it.toString()) }
    }

    override fun onDestroyView() {
        lookups = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_START = "start"
        const val STATE_END = "end"
        const val DEFAULT_HOUR = 7
    }
}
