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
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentHomeBinding
import id.sch.santaagnes.sim.databinding.ItemHomeTaskBinding
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.ui.task.countdownText
import id.sch.santaagnes.sim.ui.task.dueColor
import id.sch.santaagnes.sim.ui.task.dueDateText
import kotlinx.coroutines.launch

/** Beranda siswa dan wali murid. Pintasan Nilai dan Izin tetap ada di sini karena belum punya tab sendiri. */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private var shownChildren: List<NamedId>? = null

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    private fun render(state: HomeUiState) {
        binding.tvName.text = state.name.orEmpty()
        binding.progress.isVisible = state.isLoading
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvRemindersEmpty.isVisible = state.isRemindersEmpty || state.hasNoChild
        binding.tvRemindersEmpty.setText(if (state.hasNoChild) R.string.home_no_child else R.string.home_reminders_empty)
        renderReminders(state.reminders)
        renderChildren(state)
        renderShortcuts(state)
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
                    isCheckable = true
                    setOnClickListener { viewModel.selectChild(child.id) }
                }
                binding.chipChildren.addView(chip)
            }
        }
        val selected = state.children.indexOfFirst { it.id == state.selectedChildId }
        (binding.chipChildren.getChildAt(selected) as? Chip)?.isChecked = true
    }

    // Tugas hanya ada di graph siswa; izin hanya diajukan wali murid; nilai untuk keduanya.
    private fun renderShortcuts(state: HomeUiState) {
        val role = state.role
        val nav = findNavController()
        binding.btnTasks.isVisible = role == "STUDENT"
        binding.btnTasks.setOnClickListener { nav.navigate(R.id.studentTasksFragment) }
        binding.btnGrades.isVisible = role == "STUDENT" || role == "GUARDIAN"
        binding.btnGrades.setOnClickListener {
            nav.navigate(if (role == "GUARDIAN") R.id.guardianGradeReportFragment else R.id.studentGradeReportFragment)
        }
        binding.btnLeave.isVisible = role == "GUARDIAN"
        binding.btnLeave.setOnClickListener { nav.navigate(R.id.guardianLeaveFragment) }
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
}
