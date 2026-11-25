package id.sch.santaagnes.sim.ui.notification

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
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentNotificationListBinding
import id.sch.santaagnes.sim.domain.model.NotificationType
import kotlinx.coroutines.launch

/** Tab Notifikasi semua role (F-NTF-01): riwayat dari Room. */
@AndroidEntryPoint
class NotificationListFragment : Fragment() {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationListViewModel by viewModels()

    private val adapter = NotificationAdapter { item ->
        if (!item.isRead) viewModel.markRead(item.id)
        navigateForNotification(item.type)
    }

    private fun navigateForNotification(type: NotificationType) {
        val nav = findNavController()
        val graphId = nav.currentDestination?.parent?.id
        val destinationId = when (type) {
            NotificationType.ANNOUNCEMENT -> R.id.action_global_announcements
            NotificationType.BILL_OVERDUE -> R.id.action_global_bills
            NotificationType.SCHEDULE_CHANGE -> when (graphId) {
                R.id.student_graph -> R.id.studentScheduleFragment
                R.id.guardian_graph -> R.id.guardianScheduleFragment
                R.id.teacher_graph -> R.id.teacherScheduleFragment
                R.id.admin_graph -> R.id.changeListFragment
                else -> null
            }
            NotificationType.TASK -> when (graphId) {
                R.id.student_graph -> R.id.studentTasksFragment
                R.id.teacher_graph -> R.id.teacherTaskListFragment
                else -> null
            }
            NotificationType.VIOLATION -> when (graphId) {
                R.id.student_graph -> R.id.studentRiwayatPelanggaranFragment
                R.id.guardian_graph -> R.id.guardianRiwayatPelanggaranFragment
                R.id.teacher_graph -> R.id.teacherRiwayatPelanggaranFragment
                R.id.admin_graph -> R.id.adminRiwayatPelanggaranFragment
                else -> null
            }
            NotificationType.LEAVE_REQUEST -> when (graphId) {
                R.id.guardian_graph -> R.id.guardianLeaveFragment
                R.id.teacher_graph -> R.id.homeroomPendingFragment
                else -> null
            }
            NotificationType.BOOKING -> when (graphId) {
                R.id.student_graph -> R.id.studentRiwayatPeminjamanFragment
                R.id.teacher_graph -> R.id.teacherRiwayatPeminjamanFragment
                R.id.admin_graph -> R.id.adminPeminjamanFragment
                else -> null
            }
            NotificationType.OTHER -> null
        } ?: return
        runCatching { nav.navigate(destinationId) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter
        binding.btnMarkAll.setOnClickListener { viewModel.markAllRead() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.isLoading
                    binding.tvEmpty.isVisible = state.isEmpty
                    binding.btnMarkAll.isVisible = state.unreadCount > 0
                    adapter.submitList(state.items.orEmpty())
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        binding.rvNotifications.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
