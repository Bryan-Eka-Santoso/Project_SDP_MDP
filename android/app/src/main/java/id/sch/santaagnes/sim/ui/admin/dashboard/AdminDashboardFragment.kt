package id.sch.santaagnes.sim.ui.admin.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentAdminDashboardBinding
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
        super.onViewCreated(view, savedInstanceState)

        setupQuickMenu()
        observeViewModel()
    }

    private fun setupQuickMenu() {
        // Menu Manajemen membuka Manajemen User & Siswa (Milik Yosua)
        binding.btnMenuManagement.setOnClickListener {
            findNavController().navigate(R.id.action_adminDashboard_to_adminUser)
        }

        binding.btnMenuReports.setOnClickListener {
            Toast.makeText(requireContext(), "Menuju Laporan Admin (F-LAP)", Toast.LENGTH_SHORT).show()
        }

        binding.btnMenuPayments.setOnClickListener {
            Toast.makeText(requireContext(), "Menuju Pembayaran SPP (F-BYR)", Toast.LENGTH_SHORT).show()
        }

        binding.btnMenuOperations.setOnClickListener {
            Toast.makeText(requireContext(), "Menuju Operasional & Peminjaman (F-FAS)", Toast.LENGTH_SHORT).show()
        }

        binding.btnViewAllActivities.setOnClickListener {
            Toast.makeText(requireContext(), "Menampilkan semua log aktivitas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvAdminName.text = state.adminName
                    binding.tvStatStudents.text = state.totalStudents.toString()
                    binding.tvStatAlumni.text = state.totalAlumni.toString()
                    binding.tvStatClasses.text = state.totalClasses.toString()
                    binding.tvStatUsers.text = state.totalUsers.toString()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
