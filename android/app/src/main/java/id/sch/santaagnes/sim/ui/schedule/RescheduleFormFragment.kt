package id.sch.santaagnes.sim.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.clearErrorOnEdit
import id.sch.santaagnes.sim.databinding.FragmentRescheduleFormBinding
import id.sch.santaagnes.sim.domain.model.NamedId
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Form guru untuk memindah atau membatalkan satu pertemuan dari jadwal mengajarnya (F-JDW-04). */
@AndroidEntryPoint
class RescheduleFormFragment : Fragment() {

    private var _binding: FragmentRescheduleFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RescheduleFormViewModel by viewModels()
    private val args: RescheduleFormFragmentArgs by navArgs()

    private val target by lazy {
        RescheduleTarget(
            slotId = args.slotId,
            date = LocalDate.parse(args.date),
            startTime = LocalTime.parse(args.startTime),
            endTime = LocalTime.parse(args.endTime)
        )
    }
    private val hasMove get() = args.changeId != 0

    private var newDate: LocalDate? = null
    private var newStart: LocalTime? = null
    private var newEnd: LocalTime? = null
    private var rooms: List<NamedId> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRescheduleFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.tvLesson.text = getString(R.string.task_subject_class, args.subject, args.classGroup)
        binding.tvOriginal.text = getString(
            R.string.reschedule_original,
            DateTimeWib.formatDate(target.date),
            DateTimeWib.formatTime(target.startTime),
            DateTimeWib.formatTime(target.endTime),
            args.room ?: getString(R.string.schedule_room_none)
        )
        listOf(binding.layoutNewDate, binding.layoutNewStart, binding.layoutNewEnd, binding.layoutDescription)
            .forEach { it.clearErrorOnEdit() }

        if (savedInstanceState == null) {
            // Mengubah pemindahan yang sudah ada: isi form dengan nilai yang berlaku sekarang.
            newDate = args.newDate?.let(LocalDate::parse)
            newStart = args.newStartTime?.let(LocalTime::parse)
            newEnd = args.newEndTime?.let(LocalTime::parse)
            binding.inputDescription.setText(args.description)
            binding.inputRoom.setText(args.newRoom.orEmpty(), false)
        } else {
            newDate = savedInstanceState.getString(STATE_DATE)?.let(LocalDate::parse)
            newStart = savedInstanceState.getString(STATE_START)?.let(LocalTime::parse)
            newEnd = savedInstanceState.getString(STATE_END)?.let(LocalTime::parse)
        }
        showValues()

        binding.inputNewDate.setOnClickListener { pickDate() }
        binding.inputNewStart.setOnClickListener { pickTime(isStart = true) }
        binding.inputNewEnd.setOnClickListener { pickTime(isStart = false) }
        binding.btnSave.setOnClickListener { save() }
        binding.btnCancelMove.isVisible = hasMove
        binding.btnCancelMove.setOnClickListener { confirmCancel() }
        binding.btnCancelClass.setOnClickListener { confirmCancelClass() }
        if (args.cancelled) showCancelledMode()

        viewModel.loadRooms()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.done.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun render(state: RescheduleFormUiState) {
        binding.progress.isVisible = state.isLoadingRooms || state.isSaving
        binding.btnSave.isEnabled = !state.isSaving
        binding.btnCancelMove.isEnabled = !state.isSaving
        binding.btnCancelClass.isEnabled = !state.isSaving
        binding.layoutNewDate.error = state.dateError?.let(::getString)
        binding.layoutNewEnd.error = state.timeError?.let(::getString)
        binding.layoutDescription.error = state.descriptionError?.let(::getString)
        binding.tvError.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage

        if (state.rooms != rooms) {
            rooms = state.rooms
            binding.inputRoom.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, rooms.map { it.name })
            )
        }
    }

    private fun save() {
        // Ruangan dikenali dari teks yang tampil, sehingga tetap benar setelah layar diputar.
        val chosenRoom = binding.inputRoom.text.toString()
        viewModel.save(
            target = target,
            newDate = newDate,
            newStartTime = newStart,
            newEndTime = newEnd,
            room = rooms.firstOrNull { it.name == chosenRoom },
            description = binding.inputDescription.text.toString()
        )
    }

    private fun confirmCancel() {
        if (args.cancelled) {
            // Kelas yang dibatalkan: menghapus perubahan berarti jadwal kembali normal.
            viewModel.cancelMove(args.changeId, R.string.reschedule_restored)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reschedule_cancel_title)
            .setMessage(R.string.reschedule_cancel_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reschedule_cancel_move) { _, _ -> viewModel.cancelMove(args.changeId) }
            .show()
    }

    // Keterangan wajib sebagai alasan pembatalan; dicek dulu sebelum dialog konfirmasi.
    private fun confirmCancelClass() {
        val description = binding.inputDescription.text.toString()
        if (!viewModel.checkCancelDescription(description)) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reschedule_cancel_class_title)
            .setMessage(R.string.reschedule_cancel_class_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reschedule_cancel_class_confirm) { _, _ ->
                viewModel.cancelClass(target, binding.inputDescription.text.toString())
            }
            .show()
    }

    // Pertemuan sudah dibatalkan: sembunyikan form dan tawarkan pemulihan saja.
    private fun showCancelledMode() {
        listOf(
            binding.tvInfo, binding.layoutNewDate, binding.layoutNewStart, binding.layoutNewEnd,
            binding.layoutRoom, binding.layoutDescription, binding.btnSave, binding.btnCancelClass
        ).forEach { it.isVisible = false }
        binding.tvCancelledInfo.isVisible = true
        binding.btnCancelMove.isVisible = true
        binding.btnCancelMove.setText(R.string.reschedule_restore)
    }

    private fun pickDate() {
        val today = DateTimeWib.today()
        val start = (newDate ?: maxOf(target.date, today)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.reschedule_new_date)
            .setSelection(start)
            // Tanggal baru tidak boleh sebelum hari ini; hari Minggu dan hari jadwal itu sendiri ditolak saat simpan.
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(
                        DateValidatorPointForward.from(today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    )
                    .build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            showValues()
        }
        picker.show(childFragmentManager, "newDate")
    }

    private fun pickTime(isStart: Boolean) {
        val initial = (if (isStart) newStart else newEnd) ?: if (isStart) target.startTime else target.endTime
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(initial.hour)
            .setMinute(initial.minute)
            .setTitleText(if (isStart) R.string.reschedule_new_start else R.string.reschedule_new_end)
            .build()
        picker.addOnPositiveButtonClickListener {
            val chosen = LocalTime.of(picker.hour, picker.minute)
            if (isStart) newStart = chosen else newEnd = chosen
            showValues()
        }
        picker.show(childFragmentManager, if (isStart) "newStart" else "newEnd")
    }

    private fun showValues() {
        binding.inputNewDate.setText(newDate?.let(DateTimeWib::formatDate).orEmpty())
        binding.inputNewStart.setText(newStart?.let(DateTimeWib::formatTime).orEmpty())
        binding.inputNewEnd.setText(newEnd?.let(DateTimeWib::formatTime).orEmpty())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        newDate?.let { outState.putString(STATE_DATE, it.toString()) }
        newStart?.let { outState.putString(STATE_START, it.toString()) }
        newEnd?.let { outState.putString(STATE_END, it.toString()) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_DATE = "newDate"
        const val STATE_START = "newStart"
        const val STATE_END = "newEnd"
    }
}
