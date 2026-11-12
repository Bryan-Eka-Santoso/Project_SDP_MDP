package id.sch.santaagnes.sim.ui.schedule

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentScheduleListBinding
import id.sch.santaagnes.sim.domain.model.ScheduleScope
import id.sch.santaagnes.sim.domain.model.ScopeOption
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

@AndroidEntryPoint
class ScheduleListFragment : Fragment() {

    private var _binding: FragmentScheduleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleListViewModel by viewModels()

    private val adapter = ScheduleAdapter()

    // Pilihan yang sedang tampil sebagai chip; dibangun ulang hanya bila berubah.
    private var shownOptions: List<ScopeOption> = emptyList()
    private var currentState: ScheduleUiState? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedule.adapter = adapter

        binding.btnPrevWeek.setOnClickListener { viewModel.previousWeek() }
        binding.btnNextWeek.setOnClickListener { viewModel.nextWeek() }
        binding.btnRetry.setOnClickListener { viewModel.retry() }
        binding.btnAddTask.setOnClickListener { findNavController().navigate(R.id.action_schedule_to_taskForm) }
        binding.btnTaskList.setOnClickListener { findNavController().navigate(R.id.action_schedule_to_taskList) }
        binding.btnStudentTasks.setOnClickListener { findNavController().navigate(R.id.action_schedule_to_studentTasks) }
        binding.tabDays.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val start = currentState?.weekStart ?: return
                viewModel.selectDate(start.plusDays(tab.position.toLong()))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ScheduleUiState) {
        currentState = state
        binding.tvTitle.setText(
            if (state.role == "TEACHER") R.string.schedule_title_teacher else R.string.schedule_title
        )
        binding.tvWeek.text = DateTimeWib.formatRange(state.weekStart, state.weekEnd)
        renderScopeChips(state)

        val dayIndex = ChronoUnit.DAYS.between(state.weekStart, state.selectedDate).toInt()
        if (binding.tabDays.selectedTabPosition != dayIndex) {
            binding.tabDays.getTabAt(dayIndex)?.select()
        }

        binding.progress.isVisible = state.isLoading
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvEmpty.setText(
            if (state.role != null && state.options.isEmpty()) R.string.schedule_no_options else R.string.schedule_empty_day
        )
        binding.tvEmpty.isVisible = state.isEmpty
        // Tugas hanya dibuat guru; backend tetap memeriksa hak aksesnya.
        val isTeacher = state.role == "TEACHER"
        val isStudent = state.role == "STUDENT"
        binding.actionBar.isVisible = isTeacher || isStudent
        binding.btnStudentTasks.isVisible = isStudent
        binding.btnAddTask.isVisible = isTeacher
        binding.btnTaskList.isVisible = isTeacher
        adapter.showTeacher = state.showTeacher
        adapter.submitList(state.lessons)
    }

    private fun renderScopeChips(state: ScheduleUiState) {
        // Pemilih hanya berguna bila ada lebih dari satu pilihan (anak, atau jadwal sendiri + kelas binaan).
        binding.scrollScope.isVisible = state.options.size > 1
        if (state.options != shownOptions) {
            shownOptions = state.options
            binding.chipScope.removeAllViews()
            state.options.forEachIndexed { index, option ->
                val chip = Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = optionLabel(option)
                    isCheckable = true
                    tag = index
                    setOnClickListener { viewModel.selectOption(index) }
                }
                binding.chipScope.addView(chip)
            }
        }
        (binding.chipScope.getChildAt(state.selectedOption) as? Chip)?.isChecked = true
    }

    private fun optionLabel(option: ScopeOption): String = when (option.scope) {
        ScheduleScope.Own -> getString(R.string.schedule_scope_teaching)
        is ScheduleScope.Child -> option.name.orEmpty()
        is ScheduleScope.HomeroomClass -> getString(R.string.schedule_scope_homeroom, option.name.orEmpty())
    }

    override fun onDestroyView() {
        binding.rvSchedule.adapter = null
        shownOptions = emptyList()
        currentState = null
        _binding = null
        super.onDestroyView()
    }
}
