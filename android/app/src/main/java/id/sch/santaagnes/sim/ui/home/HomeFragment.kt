
package id.sch.santaagnes.sim.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import id.sch.santaagnes.sim.databinding.FragmentHomeBinding
import id.sch.santaagnes.sim.R
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val role = arguments
            ?.getString("role")
            ?: "UNKNOWN"

        binding.btnOpenLeave.visibility =
            if (role == "GUARDIAN") View.VISIBLE else View.GONE

        binding.btnOpenLeave.setOnClickListener {
            findNavController().navigate(
                R.id.guardianLeaveFragment
            )
        }

        binding.btnOpenGradeReport.visibility =
            if (role == "ADMIN") View.GONE else View.VISIBLE

        binding.btnOpenGradeReport.setOnClickListener {
            val destination = when (role) {
                "TEACHER" -> R.id.teacherGradeReportFragment
                "STUDENT" -> R.id.studentGradeReportFragment
                "GUARDIAN" -> R.id.guardianGradeReportFragment
                else -> null
            }

            destination?.let {
                findNavController().navigate(it)
            }
        }

        binding.btnOpenClasses.visibility =
            if (role == "TEACHER") View.VISIBLE else View.GONE

        binding.btnOpenClasses.setOnClickListener {
            findNavController().navigate(
                R.id.teacherClassesFragment
            )
        }

        binding.tvWelcome.text = getString(
            R.string.home_welcome,
            role
        )

        binding.btnCheckSession.setOnClickListener {
            viewModel.checkSession()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    binding.btnCheckSession.isEnabled = !state.isLoading
                    binding.tvSessionResult.text = state.result
                }
            }
        }
    }


    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
