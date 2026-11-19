package id.sch.santaagnes.sim.ui.admin.operations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentAdminOperationsBinding

/** Pintu masuk menu Operasional di dashboard admin: mata pelajaran, fasilitas, jenis pelanggaran, peminjaman. */
@AndroidEntryPoint
class AdminOperationsFragment : Fragment() {

    private var _binding: FragmentAdminOperationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminOperationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nav = findNavController()
        binding.cardRiwayatPelanggaran.setOnClickListener {
            nav.navigate(
                R.id.action_adminOperations_to_riwayatPelanggaran
            )
        }
        binding.btnBack.setOnClickListener { nav.popBackStack() }
        binding.cardSubjects.setOnClickListener { nav.navigate(R.id.action_adminOperations_to_subjects) }
        binding.cardElectiveAllocation.setOnClickListener { nav.navigate(R.id.action_adminOperations_to_electiveAllocation) }
        binding.cardElectiveTrends.setOnClickListener { nav.navigate(R.id.action_adminOperations_to_electiveTrends) }
        binding.cardFacilities.setOnClickListener { nav.navigate(R.id.action_adminOperations_to_facilities) }
        binding.cardViolationTypes.setOnClickListener { nav.navigate(R.id.action_adminOperations_to_violationTypes) }
        binding.cardBookings.setOnClickListener { nav.navigate(R.id.action_adminOperations_to_bookings) }
        binding.cardRiwayatPeminjaman.setOnClickListener {
            findNavController().navigate(
                R.id.adminRiwayatPeminjamanFragment
            )
        }
        binding.cardSurveiMinat.setOnClickListener {
            findNavController().navigate(
                R.id.adminSurveiMinatFragment
            )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
