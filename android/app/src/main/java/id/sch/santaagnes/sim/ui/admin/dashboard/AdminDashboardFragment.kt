package id.sch.santaagnes.sim.ui.admin.dashboard

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
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentAdminDashboardBinding
import id.sch.santaagnes.sim.databinding.ItemAdminStatBinding
import id.sch.santaagnes.sim.domain.model.AdminOverview
import id.sch.santaagnes.sim.ui.calendar.AcademicCalendarViewModel
import id.sch.santaagnes.sim.ui.calendar.bindDashboardCalendar
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminDashboardViewModel by viewModels()
    private val calendarViewModel: AcademicCalendarViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvGreeting.setText(greeting())

        val nav = findNavController()
        binding.btnMenuManagement.setOnClickListener { nav.navigate(R.id.action_adminDashboard_to_adminUser) }
        binding.btnMenuPayments.setOnClickListener { nav.navigate(R.id.action_global_bills) }
        binding.btnMenuOperations.setOnClickListener { nav.navigate(R.id.action_adminDashboard_to_operations) }
        binding.btnMenuReports.setOnClickListener { nav.navigate(R.id.action_global_reports) }
        binding.tvPeriodBadge.setOnClickListener { nav.navigate(R.id.action_adminDashboard_to_academicYear) }
        binding.cardAcademicPeriod.setOnClickListener { nav.navigate(R.id.action_adminDashboard_to_academicYear) }
        binding.cardAnnouncements.setOnClickListener { nav.navigate(R.id.action_global_announcements) }
        binding.cardCalendar.setOnClickListener { nav.navigate(R.id.action_global_calendar) }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
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

    private fun render(state: AdminDashboardUiState) {
        val displayName = state.adminName ?: getString(R.string.admin_default_name)
        binding.tvAdminName.text = displayName
        val initials = displayName.trim().split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
        binding.tvAvatarInitials.text = initials.ifEmpty { "AD" }

        val periodLabel = state.activePeriodText ?: getString(R.string.master_period_loading)
        binding.tvPeriodBadge.text = "$periodLabel ▾"
        binding.tvPeriodCardSummary.text = state.activePeriodText
            ?: getString(R.string.admin_period_manage_btn)

        binding.progress.isVisible = state.isLoading
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        renderStats(state.overview)
    }

    private fun renderStats(overview: AdminOverview) {
        val nav = findNavController()
        val stats = listOf(
            StatCardSpec(R.string.admin_stat_students, overview.students, R.drawable.ic_bar_chart) {
                nav.navigate(R.id.adminStudentFragment)
            },
            StatCardSpec(R.string.admin_stat_teachers, overview.teachers, R.drawable.ic_person) {
                nav.navigate(R.id.adminTeacherFragment)
            },
            StatCardSpec(R.string.admin_stat_classes, overview.classes, R.drawable.ic_calendar) {
                nav.navigate(R.id.adminClassFragment)
            },
            StatCardSpec(R.string.admin_stat_users, overview.users, R.drawable.ic_shield_person) {
                nav.navigate(R.id.adminUserFragment)
            },
            StatCardSpec(R.string.admin_stat_subjects, overview.subjects, R.drawable.ic_book, null),
            StatCardSpec(R.string.admin_stat_slots, overview.scheduleSlots, R.drawable.ic_calendar) {
                nav.navigate(R.id.action_adminDashboard_to_adminSchedule)
            },
            StatCardSpec(R.string.admin_stat_changes, overview.scheduleChanges, R.drawable.ic_warning, null),
            StatCardSpec(R.string.admin_stat_facilities, overview.facilities, R.drawable.ic_facility) {
                nav.navigate(R.id.action_adminDashboard_to_operations)
            }
        )

        val grid = binding.gridStats
        grid.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        stats.forEach { spec ->
            val item = ItemAdminStatBinding.inflate(inflater, grid, false)
            item.tvLabel.setText(spec.labelRes)
            item.tvValue.text = spec.value?.toString() ?: getString(R.string.admin_stat_unknown)
            item.ivStatIcon.setImageResource(spec.iconRes)
            spec.onClick?.let { handler ->
                item.root.setOnClickListener { handler() }
            }
            grid.addView(item.root)
        }
    }

    private fun greeting(): Int = when (DateTimeWib.now().hour) {
        in 4..10 -> R.string.greeting_morning
        in 11..14 -> R.string.greeting_afternoon
        in 15..17 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private data class StatCardSpec(
        val labelRes: Int,
        val value: Int?,
        val iconRes: Int,
        val onClick: (() -> Unit)?
    )
}
