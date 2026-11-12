
package id.sch.santaagnes.sim.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import id.sch.santaagnes.sim.databinding.FragmentHomeBinding
import id.sch.santaagnes.sim.ui.auth.SessionViewModel
import id.sch.santaagnes.sim.ui.auth.observeLogout
import id.sch.santaagnes.sim.R
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by viewModels()
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

        binding.tvWelcome.text = getString(
            R.string.home_welcome,
            role
        )

        binding.btnCheckSession.setOnClickListener {
            viewModel.checkSession()
        }

        // Layar sementara untuk role yang beranda aslinya belum dibuat; tetap perlu jalan keluar.
        binding.btnLogout.setOnClickListener {
            sessionViewModel.logout()
        }
        observeLogout(sessionViewModel)

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
