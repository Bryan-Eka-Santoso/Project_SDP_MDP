package id.sch.santaagnes.sim.ui.admin.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import id.sch.santaagnes.sim.databinding.FragmentAdminScheduleBinding
import id.sch.santaagnes.sim.domain.model.AdminSlot
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@AndroidEntryPoint
class AdminScheduleFragment : Fragment() {

    private var _binding: FragmentAdminScheduleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminScheduleViewModel by viewModels()

    private val adapter = AdminSlotAdapter(onEdit = ::openEdit, onDelete = ::confirmDelete)

    // Hari yang tampil sebagai chip: null = semua hari, lalu Senin sampai Sabtu.
    private val days: List<DayOfWeek?> = listOf(null) + DayOfWeek.entries.take(6)

    private var classOptions: List<Pair<Int?, String>> = emptyList()
    private var teacherOptions: List<Pair<Int?, String>> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvSlots.layoutManager = layoutManager
        binding.rvSlots.adapter = adapter
        binding.rvSlots.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
        })

        buildDayChips()
        binding.inputClass.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectClassGroup(classOptions[position].first)
        }
        binding.inputTeacher.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectTeacher(teacherOptions[position].first)
        }
        binding.btnAdd.setOnClickListener { findNavController().navigate(R.id.action_adminSchedule_to_slotForm) }
        binding.btnImport.setOnClickListener { findNavController().navigate(R.id.action_adminSchedule_to_import) }
        binding.btnChanges.setOnClickListener { findNavController().navigate(R.id.action_adminSchedule_to_changeList) }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Muat ulang tiap layar tampil agar slot yang baru dibuat atau diubah di formulir langsung terlihat.
        viewModel.refresh()
    }

    private fun buildDayChips() {
        days.forEach { day ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = day?.let(DateTimeWib::dayName) ?: getString(R.string.admin_filter_all_days)
                isCheckable = true
                setOnClickListener { viewModel.selectDay(day) }
            }
            binding.chipDays.addView(chip)
        }
    }

    private fun render(state: AdminScheduleUiState) {
        (binding.chipDays.getChildAt(days.indexOf(state.filter.dayOfWeek)) as? Chip)?.isChecked = true
        renderFilterOptions(state)

        binding.progress.isVisible = state.isLoading || state.isLoadingMore
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvEmpty.isVisible = state.isEmpty
        adapter.submitList(state.slots)
    }

    private fun renderFilterOptions(state: AdminScheduleUiState) {
        binding.filterRow.isVisible = state.classGroups.isNotEmpty() || state.teachers.isNotEmpty()

        val classes = listOf<Pair<Int?, String>>(null to getString(R.string.admin_filter_all_classes)) +
            state.classGroups.map { it.id to it.name }
        if (classes != classOptions) {
            classOptions = classes
            binding.inputClass.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, classes.map { it.second })
            )
        }
        binding.inputClass.setText(classes.firstOrNull { it.first == state.filter.classGroupId }?.second, false)

        val teachers = listOf<Pair<Int?, String>>(null to getString(R.string.admin_filter_all_teachers)) +
            state.teachers.map { it.id to it.name }
        if (teachers != teacherOptions) {
            teacherOptions = teachers
            binding.inputTeacher.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, teachers.map { it.second })
            )
        }
        binding.inputTeacher.setText(teachers.firstOrNull { it.first == state.filter.teacherId }?.second, false)
    }

    private fun openEdit(slot: AdminSlot) {
        findNavController().navigate(
            AdminScheduleFragmentDirections.actionAdminScheduleToSlotForm(
                slotId = slot.id,
                assignmentId = slot.assignmentId,
                roomId = slot.roomId ?: 0,
                dayOfWeek = slot.dayOfWeek.name,
                startTime = slot.startTime.toString(),
                endTime = slot.endTime.toString()
            )
        )
    }

    private fun confirmDelete(slot: AdminSlot) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.admin_slot_delete_title)
            .setMessage(
                getString(
                    R.string.admin_slot_delete_message,
                    slot.subjectName,
                    slot.classGroupName,
                    DateTimeWib.dayName(slot.dayOfWeek),
                    DateTimeWib.formatTime(slot.startTime)
                )
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.task_delete) { _, _ -> viewModel.delete(slot.id) }
            .show()
    }

    override fun onDestroyView() {
        binding.rvSlots.adapter = null
        classOptions = emptyList()
        teacherOptions = emptyList()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 3
    }
}
