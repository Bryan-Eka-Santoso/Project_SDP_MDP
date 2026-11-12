
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

import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch
import id.sch.santaagnes.sim.R
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()
    private var navigated = false

    private var loginSuccessShown = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(
            inflater, container, false
        )
            inflater,
            container,
            false
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
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text
                .toString()
                .trim()

            val password = binding.etPassword.text
                .toString()

            viewModel.login(email, password)
        }
    }

    private fun observeViewModel() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progressBar.isVisible =
                        state.isLoading
                    binding.progressLogin.visibility =
                        if (state.isLoading) View.VISIBLE
                        else View.GONE

                    binding.btnLogin.isEnabled =
                        !state.isLoading

                    if (state.errorMessage != null) {
                        Toast.makeText(
                            requireContext(),
                            state.errorMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }


                    if (state.isLoginSuccess && !navigated) {

                        val destination = when (state.role) {
                            "ADMIN" -> R.id.admin_graph
                            "TEACHER" -> R.id.teacher_graph
                            "STUDENT" -> R.id.student_graph
                            "GUARDIAN" -> R.id.guardian_graph
                            else -> null
                        }

                        if (destination == null) {
                            navigated = false

                            Toast.makeText(
                                requireContext(),
                                "Role pengguna tidak dikenal.",
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

                    state.errorMessage?.let { message ->
                        Toast.makeText(
                            requireContext(),
                            message,
                            Toast.LENGTH_SHORT
                        ).show()

                        viewModel.clearError()
                    }

                    if (state.isLoginSuccess &&
                        !loginSuccessShown
                    ) {
                        loginSuccessShown = true


                        Toast.makeText(
                            requireContext(),
                            getString(R.string.login_success),
                            Toast.LENGTH_SHORT
                        ).show()

                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
        super.onDestroyView()
        _binding = null
    }
}
