package id.sch.santaagnes.sim.ui.calendar

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.DialogAcademicEventFormBinding
import id.sch.santaagnes.sim.databinding.FragmentAcademicCalendarBinding
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import id.sch.santaagnes.sim.ui.admin.master.bindDateField
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

@AndroidEntryPoint
class AcademicCalendarFragment : Fragment() {

    private var _binding: FragmentAcademicCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AcademicCalendarViewModel by viewModels()

    private val adapter by lazy {
        AcademicEventAdapter(
            isAdmin = { viewModel.uiState.value.isAdmin },
            onSaveToDeviceCalendar = ::exportToDeviceCalendar,
            onEdit = { showEventFormSheet(it) },
            onDelete = ::confirmDelete
        )
    }

    private val calendarAdapter by lazy {
        CalendarDaysAdapter { date -> viewModel.selectDate(date) }
    }

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAcademicCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCalendarDays.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.rvCalendarDays.adapter = calendarAdapter
        binding.rvCalendarDays.itemAnimator = null

        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEvents.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAdd.setOnClickListener { showEventFormSheet(null) }
        binding.btnPrevMonth.setOnClickListener { viewModel.previousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }
        binding.btnToday.setOnClickListener { viewModel.goToToday() }
        binding.btnToggleScope.setOnClickListener {
            if (viewModel.uiState.value.selectedDate != null) {
                viewModel.clearDateSelection()
            } else {
                viewModel.toggleShowAllMonths()
            }
        }

        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupFilterAndSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                viewModel.setSearchQuery(editable?.toString().orEmpty())
            }
        }

        binding.btnFilterAll.setOnClickListener { viewModel.setTypeFilter(null) }
        binding.btnFilterExam.setOnClickListener { viewModel.setTypeFilter(AcademicEventType.EXAM) }
        binding.btnFilterHoliday.setOnClickListener { viewModel.setTypeFilter(AcademicEventType.HOLIDAY) }
        binding.btnFilterActivity.setOnClickListener { viewModel.setTypeFilter(AcademicEventType.ACTIVITY) }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val agenda = state.agendaEvents
                        binding.btnAdd.isVisible = state.isAdmin
                        binding.progressBar.isVisible = state.isLoading || state.isSubmitting
                        binding.tvEmptyState.isVisible = !state.isLoading && agenda.isEmpty()
                        binding.tvEmptyState.text = when {
                            state.selectedDate != null ->
                                "Tidak ada agenda pada tanggal ini. Ketuk tanggal lagi untuk melihat semua agenda bulan ini."
                            state.searchQuery.isBlank() && !state.showAllMonths && state.events.isNotEmpty() ->
                                "Tidak ada agenda pada ${DateTimeWib.formatMonthYear(state.displayedMonth)}. Ketuk \"Lihat Semua\" untuk agenda lainnya."
                            else -> getString(R.string.calendar_empty)
                        }

                        binding.tvMonthTitle.text = DateTimeWib.formatMonthYear(state.displayedMonth)
                        calendarAdapter.submitList(state.calendarDays())

                        val selectedDate = state.selectedDate
                        binding.tvAgendaHeader.text = when {
                            selectedDate != null -> "Agenda ${DateTimeWib.formatDate(selectedDate)}"
                            state.searchQuery.isNotBlank() -> "Hasil Pencarian (${agenda.size})"
                            state.showAllMonths -> "Semua Agenda (${agenda.size})"
                            else -> "Agenda ${DateTimeWib.formatMonthYear(state.displayedMonth)} (${agenda.size})"
                        }
                        binding.btnToggleScope.isVisible = state.searchQuery.isBlank()
                        binding.btnToggleScope.text = when {
                            selectedDate != null -> "Semua Bulan Ini"
                            state.showAllMonths -> "Bulan Ini Saja"
                            else -> "Lihat Semua"
                        }

                        renderTypeFilter(state.selectedType)
                        adapter.submitList(agenda)
                    }
                }

                launch {
                    viewModel.message.collect { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun renderTypeFilter(activeType: AcademicEventType?) {
        val pills = listOf(
            binding.btnFilterAll to null,
            binding.btnFilterExam to AcademicEventType.EXAM,
            binding.btnFilterHoliday to AcademicEventType.HOLIDAY,
            binding.btnFilterActivity to AcademicEventType.ACTIVITY
        )
        pills.forEach { (view, type) ->
            val selected = activeType == type
            view.setBackgroundResource(
                if (selected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
            )
            view.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.agnesian_blue else R.color.agnesian_text
                )
            )
        }
    }

    private fun showEventFormSheet(existing: AcademicEvent?) {
        val d = DialogAcademicEventFormBinding.inflate(layoutInflater)
        var selectedType = existing?.type ?: AcademicEventType.ACTIVITY

        fun renderTypePills() {
            val pills = listOf(
                d.btnTypeExam to AcademicEventType.EXAM,
                d.btnTypeHoliday to AcademicEventType.HOLIDAY,
                d.btnTypeActivity to AcademicEventType.ACTIVITY
            )
            pills.forEach { (btn, type) ->
                val selected = selectedType == type
                btn.setBackgroundResource(
                    if (selected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
                )
                btn.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (selected) R.color.agnesian_blue else R.color.agnesian_text
                    )
                )
            }
        }

        d.btnTypeExam.setOnClickListener {
            selectedType = AcademicEventType.EXAM
            renderTypePills()
        }
        d.btnTypeHoliday.setOnClickListener {
            selectedType = AcademicEventType.HOLIDAY
            renderTypePills()
        }
        d.btnTypeActivity.setOnClickListener {
            selectedType = AcademicEventType.ACTIVITY
            renderTypePills()
        }
        renderTypePills()

        val defaultDate = (viewModel.uiState.value.selectedDate ?: DateTimeWib.today()).toString()
        d.etTitle.setText(existing?.title.orEmpty())
        d.etStartDate.setText(existing?.startDate ?: defaultDate)
        d.etEndDate.setText(existing?.endDate ?: defaultDate)
        d.etDescription.setText(existing?.description.orEmpty())

        bindDateField(d.layoutStartDate, d.etStartDate, childFragmentManager, "Tanggal Mulai")
        bindDateField(d.layoutEndDate, d.etEndDate, childFragmentManager, "Tanggal Selesai")

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(
                if (existing == null) R.string.calendar_add_title else R.string.calendar_edit_title
            ),
            subtitle = "Agenda ini terlihat oleh seluruh siswa, guru, dan wali murid",
            contentView = d.root,
            primaryLabel = if (existing == null) "Tambahkan" else "Simpan"
        ) {
            val title = d.etTitle.text?.toString()?.trim().orEmpty()
            val start = d.etStartDate.text?.toString()?.trim().orEmpty()
            val end = d.etEndDate.text?.toString()?.trim().orEmpty()
            val desc = d.etDescription.text?.toString()?.trim()

            if (title.isEmpty() || start.isEmpty() || end.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Judul, tanggal mulai, dan tanggal selesai wajib diisi",
                    Toast.LENGTH_SHORT
                ).show()
                return@showAgnesianFormSheet false
            }
            if (start > end) {
                Toast.makeText(
                    requireContext(),
                    "Tanggal selesai tidak boleh sebelum tanggal mulai",
                    Toast.LENGTH_SHORT
                ).show()
                return@showAgnesianFormSheet false
            }

            viewModel.saveEvent(existing?.id, title, selectedType, start, end, desc)
            true
        }
    }

    /** Membuka aplikasi Kalender Android via Intent CalendarContract (acara sepanjang hari). */
    private fun exportToDeviceCalendar(event: AcademicEvent) {
        val intent = runCatching {
            val startMillis = LocalDate.parse(event.startDate)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
            // CalendarContract memakai batas akhir eksklusif untuk acara sepanjang hari.
            val endMillis = LocalDate.parse(event.endDate)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, "[${event.type.label}] ${event.title}")
                .putExtra(CalendarContract.Events.DESCRIPTION, event.description.orEmpty())
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }.getOrNull() ?: return

        runCatching {
            startActivity(intent)
        }.onFailure {
            Toast.makeText(
                requireContext(),
                "Tidak ada aplikasi kalender pada perangkat",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDelete(event: AcademicEvent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Agenda?")
            .setMessage("Agenda \"${event.title}\" akan dihapus permanen dari kalender akademik.")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteEvent(event.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
