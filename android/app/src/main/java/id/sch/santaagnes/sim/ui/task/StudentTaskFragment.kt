package id.sch.santaagnes.sim.ui.task

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentStudentTaskBinding
import id.sch.santaagnes.sim.domain.model.Todo
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentTaskFragment : Fragment() {

    private var _binding: FragmentStudentTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StudentTaskViewModel by viewModels()

    private val adapter = StudentTaskAdapter(
        onTaskDone = { id, done -> viewModel.setTaskDone(id, done) },
        onTodoCompleted = { id, completed -> viewModel.setTodoCompleted(id, completed) },
        onTodoDelete = ::confirmDelete
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvItems.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRetry.setOnClickListener { viewModel.reload() }
        binding.btnAddReminder.setOnClickListener {
            findNavController().navigate(R.id.action_studentTasks_to_todoForm)
        }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(StudentTaskTab.entries[tab.position])
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

    private fun render(state: StudentTaskUiState) {
        val tasksTab = state.tab == StudentTaskTab.TASKS
        if (binding.tabs.selectedTabPosition != state.tab.ordinal) {
            binding.tabs.getTabAt(state.tab.ordinal)?.select()
        }

        // Pengingat langsung dari perangkat; hanya tab tugas guru yang menunggu server.
        binding.progress.isVisible = tasksTab && state.isLoading
        binding.errorBanner.isVisible = tasksTab && state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.btnAddReminder.isVisible = !tasksTab

        val items = if (tasksTab) {
            state.groups.flatMap { group ->
                listOf<StudentTaskItem>(StudentTaskItem.SubjectHeader(group.subjectName)) +
                    group.rows.map { StudentTaskItem.TeacherTask(it) }
            }
        } else {
            state.todos.map { StudentTaskItem.Reminder(it) }
        }
        adapter.submitList(items)

        binding.tvEmpty.setText(if (tasksTab) R.string.task_empty_student else R.string.todo_empty)
        binding.tvEmpty.isVisible = if (tasksTab) state.isTasksEmpty else state.todos.isEmpty()
    }

    private fun confirmDelete(todo: Todo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.todo_delete_title)
            .setMessage(getString(R.string.todo_delete_message, todo.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.task_delete) { _, _ -> viewModel.deleteTodo(todo.id) }
            .show()
    }

    override fun onDestroyView() {
        binding.rvItems.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
