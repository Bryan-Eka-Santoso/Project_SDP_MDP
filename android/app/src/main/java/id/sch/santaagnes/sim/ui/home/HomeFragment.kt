package id.sch.santaagnes.sim.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentHomeBinding
import id.sch.santaagnes.sim.databinding.ItemHomeAnnouncementBinding
import id.sch.santaagnes.sim.databinding.ItemHomeTaskBinding
import id.sch.santaagnes.sim.domain.model.Announcement
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.ui.calendar.AcademicCalendarViewModel
import id.sch.santaagnes.sim.ui.calendar.bindDashboardCalendar
import id.sch.santaagnes.sim.ui.common.applyAgnesianFilterStyle
import id.sch.santaagnes.sim.ui.task.countdownText
import id.sch.santaagnes.sim.ui.task.dueColor
import id.sch.santaagnes.sim.ui.task.dueDateText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** Beranda siswa dan wali murid (Figma Halaman Dashboard). */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val calendarViewModel: AcademicCalendarViewModel by viewModels()

    private var shownChildren: List<NamedId>? = null
    private var shownRole: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvGreeting.setText(greeting())
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
        binding.rvShortcuts.layoutManager = GridLayoutManager(requireContext(), SHORTCUT_COLUMNS)
        binding.btnAnnouncementsAll.setOnClickListener { findNavController().navigate(R.id.action_global_announcements) }
        bindDashboardCalendar(binding.calendarCard, calendarViewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
        calendarViewModel.load()
    }

    private fun render(state: HomeUiState) {
        binding.tvName.text = state.name.orEmpty()
        binding.tvClass.text = state.className
        binding.tvClass.isVisible = !state.className.isNullOrBlank()
        binding.progress.isVisible = state.isLoading
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvRemindersEmpty.isVisible = state.isRemindersEmpty || state.hasNoChild
        binding.tvRemindersEmpty.setText(if (state.hasNoChild) R.string.home_no_child else R.string.home_reminders_empty)
        renderReminders(state.reminders)
        renderChildren(state)
        renderShortcuts(state)
        renderStats(state)
        renderAnnouncements(state.announcements)
    }

    private fun renderReminders(tasks: List<Task>) {
        val list = binding.reminderList
        list.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val today = DateTimeWib.today()
        tasks.forEach { task ->
            val row = ItemHomeTaskBinding.inflate(inflater, list, false)
            val context = requireContext()
            row.tvTitle.text = task.title
            row.tvSubject.text = task.subjectName
            row.tvDue.text = context.getString(
                R.string.task_due_with_countdown,
                dueDateText(context, task.dueDate, task.dueTime),
                countdownText(context, task.dueDate, today)
            )
            row.tvDue.setTextColor(dueColor(context, task.dueDate, task.dueTime))
            list.addView(row.root)
        }
    }

    private fun renderChildren(state: HomeUiState) {
        binding.scrollChildren.isVisible = state.children.size > 1
        if (state.children != shownChildren) {
            shownChildren = state.children
            binding.chipChildren.removeAllViews()
            state.children.forEach { child ->
                val chip = Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = child.name
                    applyAgnesianFilterStyle(onDarkHeader = true)
                    setOnClickListener { viewModel.selectChild(child.id) }
                }
                binding.chipChildren.addView(chip)
            }
        }
        val selected = state.children.indexOfFirst { it.id == state.selectedChildId }
        (binding.chipChildren.getChildAt(selected) as? Chip)?.isChecked = true
    }

    private fun renderStats(state: HomeUiState) {
        val stats = state.stats
        binding.tvStatScore.text = stats.averageScore?.let { String.format(Locale.US, "%.1f", it) } ?: dash()
        binding.tvStatAttendance.text = stats.attendancePercent?.let { getString(R.string.home_stat_percent, it) } ?: dash()
        binding.tvStatToday.text = stats.lessonsToday?.toString() ?: dash()
        binding.tvStatPoints.text = stats.violationPoints?.toString() ?: dash()
    }

    private fun dash(): String = getString(R.string.home_stat_empty)

    private fun renderAnnouncements(items: List<Announcement>) {
        val list = binding.announcementList
        list.removeAllViews()
        binding.tvAnnouncementsEmpty.isVisible = items.isEmpty()
        val inflater = LayoutInflater.from(requireContext())
        items.forEach { item ->
            val row = ItemHomeAnnouncementBinding.inflate(inflater, list, false)
            row.tvTitle.text = item.title
            val date = runCatching { DateTimeWib.formatDate(LocalDate.parse(item.date)) }.getOrDefault(item.date)
            row.tvMeta.text = getString(R.string.home_announcement_meta, item.authorName, date)
            row.root.setOnClickListener { findNavController().navigate(R.id.action_global_announcements) }
            list.addView(row.root)
        }
    }

    // Tile dibangun ulang hanya bila role berubah. Tugas hanya ada di graph siswa; izin dan buku
    // penghubung hanya wali murid; nilai dan pelanggaran untuk keduanya.
    private fun renderShortcuts(state: HomeUiState) {
        val role = state.role
        if (role == shownRole) return
        shownRole = role
        val nav = findNavController()
        val go = { id: Int -> { nav.navigate(id) } }
        val items = when (role) {
            "STUDENT" -> listOf(
                HomeShortcut(R.string.home_shortcut_attendance, R.drawable.ic_location, go(R.id.studentAttendanceSummaryFragment)),
                HomeShortcut(R.string.home_shortcut_violations, R.drawable.ic_warning, go(R.id.studentRiwayatPelanggaranFragment)),
                HomeShortcut(R.string.home_shortcut_booking, R.drawable.ic_building, go(R.id.studentAjukanPeminjamanFragment)),
                HomeShortcut(R.string.home_shortcut_survey, R.drawable.ic_edit, go(R.id.studentIsiSurveiMinatFragment)),
                HomeShortcut(R.string.home_shortcut_tasks, R.drawable.ic_book, go(R.id.studentTasksFragment)),
                HomeShortcut(R.string.home_shortcut_grades, R.drawable.ic_grades, go(R.id.studentGradesFragment)),
                HomeShortcut(R.string.home_shortcut_booking_history, R.drawable.ic_facility, go(R.id.studentRiwayatPeminjamanFragment)),
                HomeShortcut(R.string.home_shortcut_notes, R.drawable.ic_person, go(R.id.studentProgressNotesFragment)),
                HomeShortcut(R.string.shortcut_announcements, R.drawable.ic_bell, go(R.id.action_global_announcements)),
                HomeShortcut(R.string.shortcut_calendar, R.drawable.ic_calendar, go(R.id.action_global_calendar)),
                HomeShortcut(
                    R.string.home_shortcut_subject_package,
                    R.drawable.ic_package,
                    go(R.id.studentPilihPaketFragment)
                ),
            )
            "GUARDIAN" -> listOf(
                HomeShortcut(R.string.home_shortcut_violations, R.drawable.ic_warning, go(R.id.guardianRiwayatPelanggaranFragment)),
                HomeShortcut(R.string.home_shortcut_grades, R.drawable.ic_grades, go(R.id.guardianGradesFragment)),
                HomeShortcut(R.string.home_shortcut_leave, R.drawable.ic_check, go(R.id.guardianLeaveFragment)),
                HomeShortcut(R.string.home_shortcut_liaison, R.drawable.ic_book, go(R.id.guardianBacaBukuPenghubungFragment)),
                HomeShortcut(R.string.shortcut_announcements, R.drawable.ic_bell, go(R.id.action_global_announcements)),
                HomeShortcut(R.string.shortcut_calendar, R.drawable.ic_calendar, go(R.id.action_global_calendar))
            )
            else -> emptyList()
        }
        binding.rvShortcuts.adapter = HomeShortcutAdapter(items)
    }

    private fun greeting(): Int = when (DateTimeWib.now().hour) {
        in 4..10 -> R.string.greeting_morning
        in 11..14 -> R.string.greeting_afternoon
        in 15..17 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }

    override fun onDestroyView() {
        shownRole = null
        shownChildren = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val SHORTCUT_COLUMNS = 4
    }
}
