package id.sch.santaagnes.sim.ui.admin.schedule

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
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentChangeListBinding
import id.sch.santaagnes.sim.domain.model.AdminChange
import id.sch.santaagnes.sim.domain.model.ChangeType
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChangeListFragment : Fragment() {

    private var _binding: FragmentChangeListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangeListViewModel by viewModels()

    private val adapter = AdminChangeAdapter(onEdit = ::openEdit, onDelete = ::confirmDelete)

    // Jenis yang tampil sebagai chip: null = semua, lalu tiga jenis perubahan.
    private val types: List<ChangeType?> = listOf(null) + ChangeType.entries

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvChanges.layoutManager = layoutManager
        binding.rvChanges.adapter = adapter
        binding.rvChanges.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
        })

        buildTypeChips()
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAdd.setOnClickListener { findNavController().navigate(R.id.action_changeList_to_changeForm) }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Muat ulang tiap layar tampil agar perubahan yang baru disimpan di formulir langsung terlihat.
        viewModel.refresh()
    }

    private fun buildTypeChips() {
        types.forEach { type ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(
                    when (type) {
                        null -> R.string.admin_filter_all_types
                        ChangeType.SUBSTITUTE -> R.string.schedule_change_substitute
                        ChangeType.RESCHEDULE -> R.string.schedule_change_reschedule
                        ChangeType.CANCEL -> R.string.schedule_change_cancel
                    }
                )
                isCheckable = true
                setOnClickListener { viewModel.selectType(type) }
            }
            binding.chipTypes.addView(chip)
        }
    }

    private fun render(state: ChangeListUiState) {
        (binding.chipTypes.getChildAt(types.indexOf(state.type)) as? Chip)?.isChecked = true
        binding.progress.isVisible = state.isLoading || state.isLoadingMore
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvEmpty.isVisible = state.isEmpty
        adapter.submitList(state.changes)
    }

    private fun openEdit(change: AdminChange) {
        findNavController().navigate(
            ChangeListFragmentDirections.actionChangeListToChangeForm(
                slotId = change.slot.id,
                date = change.date.toString(),
                type = change.type.name,
                description = change.description,
                substituteTeacherId = change.substituteTeacherId ?: 0,
                newRoomId = change.newRoomId ?: 0,
                newStartTime = change.newStartTime?.toString(),
                newEndTime = change.newEndTime?.toString()
            )
        )
    }

    private fun confirmDelete(change: AdminChange) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_delete_title)
            .setMessage(
                getString(
                    R.string.change_delete_message,
                    change.slot.subjectName,
                    change.slot.classGroupName,
                    DateTimeWib.formatDate(change.date)
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.task_delete) { _, _ -> viewModel.delete(change.id) }
            .show()
    }

    override fun onDestroyView() {
        binding.rvChanges.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 3
    }
}
