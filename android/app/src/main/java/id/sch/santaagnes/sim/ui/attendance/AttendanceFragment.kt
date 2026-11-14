package id.sch.santaagnes.sim.ui.attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentAttendanceBinding
import id.sch.santaagnes.sim.data.remote.AttendanceSessionDto
import id.sch.santaagnes.sim.data.remote.AttendanceSlotDto
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class AttendanceFragment : Fragment() {
    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AttendanceViewModel by viewModels()
    private val assignmentId by lazy { requireArguments().getInt("assignmentId") }
    private val statuses = linkedMapOf<Int, Spinner>()
    private var slots: List<AttendanceSlotDto> = emptyList()
    private var date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }.format(Calendar.getInstance().time)
    private var lastStudentSignature: String? = null
    private var lastSlotsSignature: String? = null
    private var selectedSlotId: Int? = null
    private val statusCodes = listOf("PRESENT", "SICK", "EXCUSED", "ABSENT")
    private val statusLabels = listOf("Hadir", "Sakit", "Izin", "Alpa")

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvSubtitle.text = arguments?.getString("assignmentLabel") ?: "Kelas yang diajar"
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.bottomNav.navTeacherHome.setOnClickListener {
            if (!findNavController().popBackStack(R.id.teacherHomeFragment, false)) {
                findNavController().navigate(R.id.teacherHomeFragment)
            }
        }
        binding.bottomNav.navTeacherClasses.setOnClickListener {
            if (!findNavController().popBackStack(R.id.teacherClassesFragment, false)) {
                findNavController().navigate(R.id.teacherClassesFragment)
            }
        }
        binding.btnMarkAllPresent.setOnClickListener {
            statuses.values.forEach { it.setSelection(0) }
        }
        binding.btnDate.text = "Tanggal: $date (pilih)"
        binding.btnDate.setOnClickListener {
            val now = Calendar.getInstance().apply {
                val parts = date.split("-").map(String::toInt)
                set(parts[0], parts[1] - 1, parts[2])
            }
            DatePickerDialog(requireContext(), { _, y, m, d ->
                date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                binding.btnDate.text = "Tanggal: $date (pilih)"
                selectedSlotId?.let { load(it) }
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
        }
        binding.btnReload.setOnClickListener { selectedSlotId?.let { load(it) } }
        binding.btnSave.setOnClickListener {
            val slotId = selectedSlotId ?: return@setOnClickListener
            val journal = binding.etJournal.text.toString().trim()
            if (journal.isBlank()) {
                binding.etJournal.error = "Materi/jurnal wajib diisi."
                return@setOnClickListener
            }
            if (statuses.isEmpty()) {
                binding.tvFeedback.text = "Muat daftar siswa terlebih dahulu."
                return@setOnClickListener
            }
            val values = statuses.mapValues { (_, spinner) -> statusCodes[spinner.selectedItemPosition] }
            viewModel.save(slotId, date, journal, values)
        }
        viewModel.loadSlots(assignmentId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.loading || state.saving) View.VISIBLE else View.GONE
                    binding.btnSave.isEnabled = !state.loading && !state.saving && state.session != null
                    binding.tvFeedback.text = state.error ?: state.message ?: if (!state.loading && state.slots.isEmpty()) "Belum ada slot mengajar aktif." else ""
                    val signature = state.slots.joinToString { it.id.toString() }
                    if (signature != lastSlotsSignature) {
                        lastSlotsSignature = signature
                        slots = state.slots
                        binding.spinnerSlots.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                            slots.map { "${dayIndo(it.dayOfWeek)} • ${it.subjectName} • ${it.className} (slot ${it.id})" })
                        if (slots.isNotEmpty()) {
                            selectedSlotId = slots.first().id
                            date = recentDay(slots.first().dayOfWeek)
                            binding.btnDate.text = "Tanggal: $date (pilih)"
                            load(slots.first().id)
                        }
                        binding.spinnerSlots.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                val slot = slots.getOrNull(position) ?: return
                                if (selectedSlotId != slot.id) {
                                    selectedSlotId = slot.id
                                    date = recentDay(slot.dayOfWeek)
                                    binding.btnDate.text = "Tanggal: $date (pilih)"
                                    load(slot.id)
                                }
                            }
                        }
                    }
                    state.session?.let { session ->
                        val sessionSignature = "${session.slotId}|${session.date}|${session.saved}|${session.students.joinToString { "${it.id}:${it.status}" }}"
                        if (sessionSignature != lastStudentSignature) {
                            lastStudentSignature = sessionSignature
                            renderStudents(session)
                            binding.etJournal.setText(session.journal)
                        }
                    }
                    if (state.session == null && !state.loading) {
                        binding.studentsContainer.removeAllViews()
                        statuses.clear()
                        lastStudentSignature = null
                    }
                }
            }
        }
    }

    private fun load(slotId: Int) {
        lastStudentSignature = null
        binding.studentsContainer.removeAllViews()
        statuses.clear()
        viewModel.loadSession(slotId, date)
    }

    private fun renderStudents(session: AttendanceSessionDto) {
        binding.studentsContainer.removeAllViews()
        statuses.clear()
        session.students.forEach { student ->
            val row = layoutInflater.inflate(
                R.layout.item_attendance_student,
                binding.studentsContainer,
                false
            )
            row.findViewById<TextView>(R.id.tvAttendanceStudent).text =
                "${student.name} (${student.nis.orEmpty()})"
            val picker = row.findViewById<Spinner>(R.id.spinnerAttendanceStatus)
            picker.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                statusLabels
            )
            picker.setSelection(statusCodes.indexOf(student.status ?: "PRESENT").coerceAtLeast(0))
            binding.studentsContainer.addView(row)
            statuses[student.id] = picker
        }
        binding.tvCount.text = "${session.students.size} siswa • ${if (session.saved) "Sudah dicatat" else "Belum dicatat"}"
    }

    private fun recentDay(day: String): String {
        val wanted = when (day) {
            "MONDAY" -> Calendar.MONDAY; "TUESDAY" -> Calendar.TUESDAY
            "WEDNESDAY" -> Calendar.WEDNESDAY; "THURSDAY" -> Calendar.THURSDAY
            "FRIDAY" -> Calendar.FRIDAY; "SATURDAY" -> Calendar.SATURDAY
            else -> Calendar.SUNDAY
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
        while (cal.get(Calendar.DAY_OF_WEEK) != wanted) cal.add(Calendar.DAY_OF_MONTH, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(cal.time)
    }

    private fun dayIndo(day: String): String = when (day) {
        "MONDAY" -> "Senin"; "TUESDAY" -> "Selasa"; "WEDNESDAY" -> "Rabu"
        "THURSDAY" -> "Kamis"; "FRIDAY" -> "Jumat"; "SATURDAY" -> "Sabtu"
        else -> day
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
