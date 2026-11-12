
package id.sch.santaagnes.sim.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import androidx.navigation.NavOptions

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()
    private var navigated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        binding.btnLogin.setOnClickListener {
            viewModel.login(
                binding.etEmail.text.toString(),
                binding.etPassword.text.toString()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progressBar.isVisible =
                        state.isLoading

                    binding.btnLogin.isEnabled =
                        !state.isLoading

                    val errorText = state.errorMessage
                        ?: state.errorRes?.let { getString(it) }

                    if (errorText != null) {
                        Toast.makeText(
                            requireContext(),
                            errorText,
                            Toast.LENGTH_LONG
                        ).show()
                    }


                    if (state.isLoginSuccess && !navigated) {

                        val destination = roleGraphId(state.role)

                        if (destination == null) {
                            navigated = false

                            Toast.makeText(
                                requireContext(),
                                R.string.error_unknown_role,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            navigated = true

                            val options = NavOptions.Builder()
                                .setPopUpTo(
                                    R.id.loginFragment,
                                    true
                                )
                                .build()

                            findNavController().navigate(
                                destination,
                                null,
                                options
                            )
                        }
                    }

                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
