package id.sch.santaagnes.sim.ui.calendar

import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ItemDashboardCalendarEventBinding
import id.sch.santaagnes.sim.databinding.ViewDashboardCalendarBinding
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Mengikat kartu kalender bulanan interaktif pada dashboard setiap role tanpa duplikasi kode. */
fun Fragment.bindDashboardCalendar(
    cardBinding: ViewDashboardCalendarBinding,
    viewModel: AcademicCalendarViewModel,
    onOpenFullCalendar: () -> Unit = { findNavController().navigate(R.id.action_global_calendar) }
) {
    val daysAdapter = CalendarDaysAdapter { date -> viewModel.selectDate(date) }
    cardBinding.rvCalendarDays.layoutManager = GridLayoutManager(requireContext(), 7)
    cardBinding.rvCalendarDays.adapter = daysAdapter
    cardBinding.rvCalendarDays.itemAnimator = null

    cardBinding.btnPrevMonth.setOnClickListener { viewModel.previousMonth() }
    cardBinding.btnNextMonth.setOnClickListener { viewModel.nextMonth() }
    cardBinding.btnToday.setOnClickListener { viewModel.goToToday() }
    cardBinding.btnOpenFullCalendar.setOnClickListener { onOpenFullCalendar() }

    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiState.collect { state ->
                renderDashboardCalendar(cardBinding, daysAdapter, state, onOpenFullCalendar)
            }
        }
    }
}

private fun renderDashboardCalendar(
    binding: ViewDashboardCalendarBinding,
    adapter: CalendarDaysAdapter,
    state: AcademicCalendarUiState,
    onOpenFullCalendar: () -> Unit
) {
    val ctx = binding.root.context
    binding.btnOpenFullCalendar.text = if (state.isAdmin) "Kelola" else "Selengkapnya"
    binding.tvMonthTitle.text = DateTimeWib.formatMonthYear(state.displayedMonth)
    adapter.submitList(state.calendarDays())

    val agenda = state.agendaEvents
    val selectedDate = state.selectedDate
    binding.tvAgendaSummary.text = when {
        selectedDate != null -> "Agenda ${DateTimeWib.formatDate(selectedDate)}"
        else -> "Agenda ${DateTimeWib.formatMonthYear(state.displayedMonth)}"
    }

    val list = binding.agendaList
    list.removeAllViews()
    binding.tvAgendaEmpty.isVisible = agenda.isEmpty()
    if (agenda.isEmpty()) {
        binding.tvAgendaEmpty.text = if (selectedDate != null) {
            "Tidak ada agenda pada tanggal ini."
        } else {
            "Tidak ada agenda pada ${DateTimeWib.formatMonthYear(state.displayedMonth)}."
        }
        return
    }

    val inflater = LayoutInflater.from(ctx)
    agenda.take(PREVIEW_EVENT_LIMIT).forEach { event ->
        val row = ItemDashboardCalendarEventBinding.inflate(inflater, list, false)
        val typeColor = when (event.type) {
            AcademicEventType.EXAM -> R.color.change_substitute
            AcademicEventType.HOLIDAY -> R.color.bill_paid
            AcademicEventType.ACTIVITY -> R.color.agnesian_blue
        }
        row.vTypeDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, typeColor))
        row.tvEventTitle.text = event.title
        row.tvEventMeta.text = "${event.type.label} · ${formatEventRange(event)}"
        row.root.setOnClickListener { onOpenFullCalendar() }
        list.addView(row.root)
    }
}

private fun formatEventRange(event: AcademicEvent): String = runCatching {
    val start = LocalDate.parse(event.startDate)
    val end = LocalDate.parse(event.endDate)
    if (start == end) DateTimeWib.formatDate(start) else DateTimeWib.formatRange(start, end)
}.getOrDefault(
    if (event.startDate == event.endDate) event.startDate else "${event.startDate} – ${event.endDate}"
)

private const val PREVIEW_EVENT_LIMIT = 4
