package id.sch.santaagnes.sim.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
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
    private val calendarSyncViewModel: CalendarSyncViewModel by viewModels()

    // Izin kalender diminta saat tombol sinkron dinyalakan, bukan saat aplikasi dibuka.
    private val calendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) calendarSyncViewModel.enable() else calendarSyncViewModel.permissionDenied()
        }

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

        binding.switchCalendarSync.setOnCheckedChangeListener { _, checked ->
            if (checked == calendarSyncViewModel.uiState.value.enabled) return@setOnCheckedChangeListener
            if (!checked) {
                calendarSyncViewModel.disable()
            } else if (hasCalendarPermission()) {
                calendarSyncViewModel.enable()
            } else {
                calendarPermission.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                calendarSyncViewModel.uiState.collect(::renderCalendarSync)
            }
        }

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

    private fun hasCalendarPermission() = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    ).all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }

    private fun renderCalendarSync(state: CalendarSyncUiState) {
        // Listener mengabaikan perubahan yang sama dengan state, jadi menyetel dari kode aman.
        binding.switchCalendarSync.isChecked = state.enabled
        binding.switchCalendarSync.isEnabled = !state.busy
        binding.tvCalendarSyncDesc.setText(
            if (state.includesBookings) R.string.calendar_sync_desc_with_bookings
            else R.string.calendar_sync_desc_academic
        )
        val status = when {
            state.busy -> getString(R.string.calendar_sync_busy)
            state.errorMessage != null -> state.errorMessage
            state.enabled && state.result != null && state.lastSyncMillis != null -> getString(
                R.string.calendar_sync_status,
                DateTimeWib.formatTimestamp(state.lastSyncMillis),
                state.result.total,
                state.result.added,
                state.result.updated,
                state.result.removed
            )
            state.enabled && state.lastSyncMillis != null -> getString(
                R.string.calendar_sync_status_time_only,
                DateTimeWib.formatTimestamp(state.lastSyncMillis)
            )
            else -> null
        }
        binding.tvCalendarSyncStatus.isVisible = status != null
        binding.tvCalendarSyncStatus.text = status
        binding.tvCalendarSyncStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (state.errorMessage != null) R.color.change_cancel else R.color.agnesian_blue
            )
        )
    }

    private fun render(profile: Profile) {
        binding.tvName.text = profile.name
        val baseRole = roleLabel(profile.role)
        val roleDisplay = if (profile.role == "TEACHER" && profile.homeroomClasses.isNotEmpty()) {
            "$baseRole · ${getString(R.string.profile_homeroom_badge)}"
        } else {
            baseRole
        }
        binding.tvRole.text = roleDisplay
        binding.tvSemesterBadge.text = baseRole
        binding.btnBills.isVisible = profile.canViewBills
        binding.btnReports.isVisible = profile.canViewReports

        val initials = profile.name.trim().split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
        binding.tvAvatarInitials.text = initials.ifEmpty { "SA" }

        if (!profile.identifier.isNullOrBlank()) {
            binding.layoutIdentifier.isVisible = true
            binding.tvIdentifierLabel.text = if (profile.role == "STUDENT") "NIS" else "NIP"
            binding.tvIdentifierValue.text = profile.identifier
        } else {
            binding.layoutIdentifier.isVisible = false
        }

        binding.tvFullNameValue.text = profile.name
        binding.tvRoleLabel.text = if (profile.role == "STUDENT") {
            getString(R.string.profile_class_role_label)
        } else {
            getString(R.string.profile_role_label)
        }
          
        // Peran sudah tampil sebagai chip di bawah nama; untuk siswa barisnya diganti Kelas dan
        // Wali Kelas (Figma Halaman Profil).
        val isStudent = profile.role == "STUDENT"
        binding.layoutRoleField.isVisible = !isStudent
        binding.tvRoleLabel.setText(R.string.profile_label_role)
        binding.tvRoleValue.text = roleDisplay
        binding.layoutClass.isVisible = isStudent && profile.classNames.isNotEmpty()
        binding.tvClassValue.text = profile.classNames.joinToString(", ")
        binding.layoutHomeroomTeacher.isVisible = isStudent && profile.homeroomTeachers.isNotEmpty()
        binding.tvHomeroomTeacherValue.text = profile.homeroomTeachers.joinToString(", ")
        binding.layoutPhone.isVisible = !profile.phone.isNullOrBlank()
        binding.tvPhoneValue.text = profile.phone

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
            } else if (profile.role == "TEACHER") {
                add(getString(R.string.profile_teacher_regular_note))
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
