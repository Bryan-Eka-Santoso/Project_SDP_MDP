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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminDashboardViewModel by viewModels()

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
        // Manajemen: pengguna dan siswa (tab Akademik); Pembayaran dan Laporan: layar bersama; Operasional: hub.
        binding.btnMenuManagement.setOnClickListener { nav.navigate(R.id.action_adminDashboard_to_adminUser) }
        binding.btnMenuPayments.setOnClickListener { nav.navigate(R.id.action_global_bills) }
        binding.btnMenuOperations.setOnClickListener { nav.navigate(R.id.action_adminDashboard_to_operations) }
        binding.btnMenuReports.setOnClickListener { nav.navigate(R.id.action_global_reports) }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Angka mengikuti data terbaru setelah admin menambah atau menghapus data di layar lain.
        viewModel.refresh()
    }

    private fun render(state: AdminDashboardUiState) {
        binding.tvAdminName.text = state.adminName ?: getString(R.string.admin_default_name)
        binding.progress.isVisible = state.isLoading
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        renderStats(state.overview)
    }

    private fun renderStats(overview: AdminOverview) {
        val stats = listOf(
            R.string.admin_stat_students to overview.students,
            R.string.admin_stat_teachers to overview.teachers,
            R.string.admin_stat_classes to overview.classes,
            R.string.admin_stat_users to overview.users,
            R.string.admin_stat_subjects to overview.subjects,
            R.string.admin_stat_slots to overview.scheduleSlots,
            R.string.admin_stat_changes to overview.scheduleChanges,
            R.string.admin_stat_facilities to overview.facilities
        )
        val grid = binding.gridStats
        grid.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        stats.forEach { (label, value) ->
            val item = ItemAdminStatBinding.inflate(inflater, grid, false)
            item.tvLabel.setText(label)
            item.tvValue.text = value?.toString() ?: getString(R.string.admin_stat_unknown)
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
}
