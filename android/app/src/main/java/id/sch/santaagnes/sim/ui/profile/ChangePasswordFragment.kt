package id.sch.santaagnes.sim.ui.profile

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
import id.sch.santaagnes.sim.databinding.FragmentChangePasswordBinding
import kotlinx.coroutines.launch

/** Ubah password (F-AUTH-03). Setelah berhasil, sesi di perangkat lain berakhir. */
@AndroidEntryPoint
class ChangePasswordFragment : Fragment() {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangePasswordViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnSave.setOnClickListener {
            viewModel.save(
                current = binding.inputCurrent.text.toString(),
                new = binding.inputNew.text.toString(),
                confirm = binding.inputConfirm.text.toString()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.saved.collect {
                        Toast.makeText(requireContext(), R.string.password_changed, Toast.LENGTH_LONG).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun render(state: ChangePasswordUiState) {
        binding.progress.isVisible = state.isSaving
        binding.btnSave.isEnabled = !state.isSaving
        binding.layoutCurrent.error = state.currentError?.let(::getString)
        binding.layoutNew.error = state.newError?.let(::getString)
        binding.layoutConfirm.error = state.confirmError?.let(::getString)
        binding.tvError.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
