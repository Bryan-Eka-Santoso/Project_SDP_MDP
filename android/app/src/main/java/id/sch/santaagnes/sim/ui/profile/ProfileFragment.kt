package id.sch.santaagnes.sim.ui.profile

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
import id.sch.santaagnes.sim.databinding.FragmentProfileBinding
import id.sch.santaagnes.sim.domain.model.Profile
import id.sch.santaagnes.sim.ui.auth.SessionViewModel
import id.sch.santaagnes.sim.ui.auth.observeLogout
import kotlinx.coroutines.launch

/** Tab Profil semua role (F-AUTH-03); pintu masuk ke ubah password dan tagihan. */
@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private val sessionViewModel: SessionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnLogout.setOnClickListener { sessionViewModel.logout() }
        binding.btnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_global_changePassword)
        }
        binding.btnBills.setOnClickListener { findNavController().navigate(R.id.action_global_bills) }
        binding.btnReports.setOnClickListener { findNavController().navigate(R.id.action_global_reports) }
        observeLogout(sessionViewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.isVisible = state.isRefreshing
                    binding.tvError.isVisible = state.errorMessage != null
                    binding.tvError.text = state.errorMessage
                    state.profile?.let(::render)
                }
            }
        }
    }

    private fun render(profile: Profile) {
        binding.tvName.text = profile.name
        val roleStr = roleLabel(profile.role)
        binding.tvRole.text = roleStr
        binding.btnBills.isVisible = profile.canViewBills
        binding.btnReports.isVisible = profile.canViewReports

        // Inisial avatar
        val initials = profile.name.trim().split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
        binding.tvAvatarInitials.text = if (initials.isNotEmpty()) initials else "SA"

        // NIS / NIP
        if (!profile.identifier.isNullOrBlank()) {
            binding.layoutIdentifier.isVisible = true
            binding.tvIdentifierLabel.text = if (profile.role == "STUDENT") "NIS" else "NIP"
            binding.tvIdentifierValue.text = profile.identifier
        } else {
            binding.layoutIdentifier.isVisible = false
        }

        // Nama Lengkap
        binding.tvFullNameValue.text = profile.name

        // Peran / Kelas
        binding.tvRoleLabel.text = if (profile.role == "STUDENT") "Kelas / Peran" else "Peran"
        binding.tvRoleValue.text = roleStr

        // Email
        if (!profile.email.isNullOrBlank()) {
            binding.layoutEmail.isVisible = true
            binding.tvEmailValue.text = profile.email
        } else {
            binding.layoutEmail.isVisible = false
        }

        val lines = buildList {
            if (profile.homeroomClasses.isNotEmpty()) {
                add(getString(R.string.profile_homeroom, profile.homeroomClasses.joinToString(", ")))
            }
            if (profile.children.isNotEmpty()) {
                add(getString(R.string.profile_children, profile.children.joinToString(", ")))
            }
        }
        binding.tvDetails.isVisible = lines.isNotEmpty()
        binding.tvDetails.text = lines.joinToString("\n")
    }

    private fun roleLabel(role: String): String = getString(
        when (role) {
            "STUDENT" -> R.string.role_student
            "TEACHER" -> R.string.role_teacher
            "GUARDIAN" -> R.string.role_guardian
            "ADMIN" -> R.string.role_admin
            else -> R.string.role_unknown
        }
    )

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
