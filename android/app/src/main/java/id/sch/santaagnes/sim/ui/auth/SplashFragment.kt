package id.sch.santaagnes.sim.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentSplashBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.destination.collect { destination ->
                    when (destination) {
                        null -> Unit
                        SplashDestination.Login -> openLogin()
                        is SplashDestination.Home -> openHome(destination.role)
                    }
                }
            }
        }
    }

    private fun openLogin() {
        findNavController().navigate(R.id.loginFragment, null, leaveSplash())
    }

    private fun openHome(role: String) {
        val graph = roleGraphId(role)
        if (graph == null) {
            openLogin()
        } else {
            findNavController().navigate(graph, null, leaveSplash())
        }
    }

    private fun leaveSplash() = NavOptions.Builder()
        .setPopUpTo(R.id.splashFragment, true)
        .build()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
