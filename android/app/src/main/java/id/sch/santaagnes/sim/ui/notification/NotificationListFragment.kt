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
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.databinding.FragmentNotificationListBinding
import kotlinx.coroutines.launch

/** Tab Notifikasi semua role (F-NTF-01): riwayat dari Room. */
@AndroidEntryPoint
class NotificationListFragment : Fragment() {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationListViewModel by viewModels()

    private val adapter = NotificationAdapter { item -> if (!item.isRead) viewModel.markRead(item.id) }

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

    override fun onDestroyView() {
        binding.rvNotifications.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
