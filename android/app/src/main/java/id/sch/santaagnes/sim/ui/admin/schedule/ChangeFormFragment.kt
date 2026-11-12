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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentChangeFormBinding
import id.sch.santaagnes.sim.domain.model.AdminSlot
import id.sch.santaagnes.sim.domain.model.ChangeType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@AndroidEntryPoint
class ChangeFormFragment : Fragment() {

    private var _binding: FragmentChangeFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangeFormViewModel by viewModels()
    private val args: ChangeFormFragmentArgs by navArgs()

    // Slot + tanggal adalah kunci perubahan di server; saat mengubah, keduanya dikunci.
    private val isEdit get() = args.slotId != 0

    private var date: LocalDate? = null
    private var start: LocalTime? = null
    private var end: LocalTime? = null
    private var loaded = false
    private var needsPrefill = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangeFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvHeading.setText(if (isEdit) R.string.change_form_title_edit else R.string.change_form_title_new)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.inputType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ChangeType.entries.map(::typeLabel))
        )
        binding.inputType.setOnItemClickListener { _, _, _, _ -> showTypeFields() }

        needsPrefill = savedInstanceState == null && isEdit
        if (needsPrefill) {
            date = args.date?.let(LocalDate::parse)
            start = args.newStartTime?.let(LocalTime::parse)
            end = args.newEndTime?.let(LocalTime::parse)
            args.type?.let { binding.inputType.setText(typeLabel(ChangeType.valueOf(it)), false) }
            binding.inputDescription.setText(args.description.orEmpty())
        }
        savedInstanceState?.getString(STATE_DATE)?.let { date = LocalDate.parse(it) }
        savedInstanceState?.getString(STATE_START)?.let { start = LocalTime.parse(it) }
        savedInstanceState?.getString(STATE_END)?.let { end = LocalTime.parse(it) }
        showDate()
        showTimes()
        showTypeFields()

        binding.layoutSlot.isEnabled = !isEdit
        binding.inputSlot.isEnabled = !isEdit
        binding.layoutDate.isEnabled = !isEdit
        if (!isEdit) binding.inputDate.setOnClickListener { pickDate() }
        binding.inputStart.setOnClickListener { pickTime(start) { start = it; showTimes() } }
        binding.inputEnd.setOnClickListener { pickTime(end) { end = it; showTimes() } }
        binding.btnSave.setOnClickListener { save() }
        viewModel.load()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.saved.collect { findNavController().popBackStack() } }
            }
        }
    }

    private fun render(state: ChangeFormUiState) {
        binding.progress.isVisible = state.isLoading || state.isSaving
        binding.btnSave.isEnabled = !state.isSaving && state.isReady
        binding.layoutSlot.error = state.slotError?.let(::getString)
        binding.layoutDate.error = state.dateError?.let(::getString)
        binding.layoutType.error = state.typeError?.let(::getString)
        binding.layoutDescription.error = state.descriptionError?.let(::getString)
        binding.layoutTeacher.error = state.teacherError?.let(::getString)
        binding.tvRescheduleError.isVisible = state.rescheduleError != null
        binding.tvRescheduleError.text = state.rescheduleError?.let(::getString)
        binding.tvError.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage

        val lookups = state.lookups
        if (lookups != null && !loaded) {
            loaded = true
            binding.inputSlot.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, state.slots.map(::slotLabel))
            )
            binding.inputTeacher.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, lookups.teachers.map { it.name })
            )
            binding.inputRoom.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, lookups.rooms.map { it.name })
            )
            if (needsPrefill) prefill(state)
        }
    }

    // Mengubah perubahan: slot, guru pengganti, dan ruangan awal diambil dari argumen navigasi.
    private fun prefill(state: ChangeFormUiState) {
        needsPrefill = false
        val lookups = state.lookups ?: return
        state.slots.firstOrNull { it.id == args.slotId }?.let { binding.inputSlot.setText(slotLabel(it), false) }
        lookups.teachers.firstOrNull { it.id == args.substituteTeacherId }
            ?.let { binding.inputTeacher.setText(it.name, false) }
        lookups.rooms.firstOrNull { it.id == args.newRoomId }?.let { binding.inputRoom.setText(it.name, false) }
    }

    private fun slotLabel(slot: AdminSlot): String = getString(
        R.string.change_slot_label,
        slot.classGroupName,
        slot.subjectName,
        DateTimeWib.dayName(slot.dayOfWeek),
        DateTimeWib.formatTime(slot.startTime),
        DateTimeWib.formatTime(slot.endTime)
    )

    private fun typeLabel(type: ChangeType): String = getString(
        when (type) {
            ChangeType.SUBSTITUTE -> R.string.schedule_change_substitute
            ChangeType.RESCHEDULE -> R.string.schedule_change_reschedule
            ChangeType.CANCEL -> R.string.schedule_change_cancel
        }
    )

    private fun currentType(): ChangeType? {
        val text = binding.inputType.text.toString()
        return ChangeType.entries.firstOrNull { typeLabel(it) == text }
    }

    private fun showTypeFields() {
        val type = currentType()
        binding.layoutTeacher.isVisible = type == ChangeType.SUBSTITUTE
        binding.groupReschedule.isVisible = type == ChangeType.RESCHEDULE
    }

    private fun save() {
        val state = viewModel.uiState.value
        val lookups = state.lookups
        // Pilihan dikenali dari teks yang tampil, sehingga tetap benar setelah layar diputar.
        val slotText = binding.inputSlot.text.toString()
        val teacherText = binding.inputTeacher.text.toString()
        val roomText = binding.inputRoom.text.toString()
        viewModel.save(
            slot = state.slots.firstOrNull { slotLabel(it) == slotText },
            date = date,
            type = currentType(),
            description = binding.inputDescription.text.toString(),
            teacher = lookups?.teachers?.firstOrNull { it.name == teacherText },
            room = lookups?.rooms?.firstOrNull { it.name == roomText },
            start = start,
            end = end
        )
    }

    private fun pickDate() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.change_date_hint)
            .setSelection((date ?: DateTimeWib.today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            showDate()
        }
        picker.show(childFragmentManager, "date")
    }

    private fun pickTime(current: LocalTime?, onPicked: (LocalTime) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current?.hour ?: DEFAULT_HOUR)
            .setMinute(current?.minute ?: 0)
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(LocalTime.of(picker.hour, picker.minute)) }
        picker.show(childFragmentManager, "time")
    }

    private fun showDate() {
        binding.inputDate.setText(date?.let(DateTimeWib::formatDate).orEmpty())
    }

    private fun showTimes() {
        binding.inputStart.setText(start?.let(DateTimeWib::formatTime).orEmpty())
        binding.inputEnd.setText(end?.let(DateTimeWib::formatTime).orEmpty())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        date?.let { outState.putString(STATE_DATE, it.toString()) }
        start?.let { outState.putString(STATE_START, it.toString()) }
        end?.let { outState.putString(STATE_END, it.toString()) }
    }

    override fun onDestroyView() {
        loaded = false
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_DATE = "date"
        const val STATE_START = "start"
        const val STATE_END = "end"
        const val DEFAULT_HOUR = 7
    }
}
