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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentTeacherTaskListBinding
import id.sch.santaagnes.sim.domain.model.Task
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TeacherTaskListFragment : Fragment() {

    private var _binding: FragmentTeacherTaskListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeacherTaskListViewModel by viewModels()

    private val adapter = TeacherTaskAdapter(onEdit = ::openEdit, onDelete = ::confirmDelete)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeacherTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.layoutManager = layoutManager
        binding.rvTasks.adapter = adapter
        // Halaman berikutnya dimuat saat daftar hampir habis digulir.
        binding.rvTasks.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val last = layoutManager.findLastVisibleItemPosition()
                if (dy > 0 && last >= adapter.itemCount - LOAD_MORE_THRESHOLD) viewModel.loadMore()
            }
        })

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAdd.setOnClickListener {
            findNavController().navigate(TeacherTaskListFragmentDirections.actionTaskListToTaskForm())
        }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Muat ulang tiap layar tampil agar tugas yang baru dibuat atau diubah di formulir langsung terlihat.
        viewModel.refresh()
    }

    private fun render(state: TeacherTaskListUiState) {
        binding.progress.isVisible = state.isLoading || state.isLoadingMore
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvEmpty.isVisible = state.isEmpty
        adapter.submitList(state.tasks)
    }

    private fun openEdit(task: Task) {
        findNavController().navigate(
            TeacherTaskListFragmentDirections.actionTaskListToTaskForm(
                taskId = task.id,
                title = task.title,
                description = task.description,
                dueDate = task.dueDate.toString(),
                label = getString(R.string.task_subject_class, task.subjectName, task.classGroupName)
            )
        )
    }

    private fun confirmDelete(task: Task) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.task_delete_title)
            .setMessage(getString(R.string.task_delete_message, task.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.task_delete) { _, _ -> viewModel.delete(task.id) }
            .show()
    }

    override fun onDestroyView() {
        binding.rvTasks.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 3
    }
}
