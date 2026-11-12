package id.sch.santaagnes.sim.ui.admin.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentReportHubBinding

/** Daftar laporan admin (F-LAP-*); setiap kartu membuka layar laporannya. */
@AndroidEntryPoint
class ReportHubFragment : Fragment() {

    private var _binding: FragmentReportHubBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.cardScheduleChanges.setOnClickListener {
            findNavController().navigate(R.id.action_reportHub_to_scheduleChangeReport)
        }
        binding.cardTeachingLoad.setOnClickListener {
            findNavController().navigate(R.id.action_reportHub_to_teachingLoadReport)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
