package id.sch.santaagnes.sim.ui.admin.master.users

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.DialogAddUserBinding
import id.sch.santaagnes.sim.databinding.DialogResetPasswordBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminUsersBinding
import id.sch.santaagnes.sim.domain.model.MasterUser
import id.sch.santaagnes.sim.ui.admin.master.AgnesianOption
import id.sch.santaagnes.sim.ui.admin.master.MasterTab
import id.sch.santaagnes.sim.ui.admin.master.bindFilterPill
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.setupMasterTabs
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminUserFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminUserViewModel by viewModels()

    private val roleFilterOptions by lazy {
        listOf(
            AgnesianOption("", getString(R.string.filter_all_roles), "Tampilkan seluruh akun pengguna"),
            AgnesianOption("ADMIN", getString(R.string.role_admin), "Pengelola master data, jadwal & laporan"),
            AgnesianOption("TEACHER", getString(R.string.role_teacher), "Guru pengajar dan wali kelas"),
            AgnesianOption("STUDENT", getString(R.string.role_student), "Peserta didik terdaftar"),
            AgnesianOption("GUARDIAN", getString(R.string.role_guardian), "Orang tua atau wali murid")
        )
    }

    private val userAdapter by lazy {
        AdminUserAdapter(
            onEdit = { showUserFormSheet(it) },
            onToggleStatus = ::confirmToggleStatus,
            onResetPassword = ::showResetPasswordDialog,
            onDelete = ::confirmDeleteUser
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nav = findNavController()
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = userAdapter

        setupMasterHeader(
            header = binding.header,
            navController = nav,
            titleRes = R.string.title_management_user,
            showImport = true,
            onAdd = { showUserFormSheet(null) }
        )
        setupMasterTabs(binding.tabs, nav, MasterTab.USER)
        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupFilterAndSearch() {
        binding.btnFilterRole.text = roleFilterOptions.first().title
        bindFilterPill(
            pill = binding.btnFilterRole,
            sheetTitle = "Filter Role Pengguna",
            options = { roleFilterOptions },
            selectedIndex = {
                val active = viewModel.uiState.value.currentFilter.role.orEmpty()
                roleFilterOptions.indexOfFirst { it.id == active }.coerceAtLeast(0)
            },
            onSelected = { _, option ->
                viewModel.setRoleFilter(option.id.ifBlank { null })
            }
        )

        binding.etSearchUser.addTextChangedListener { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }

        binding.chipActive.setOnClickListener { viewModel.setStatusFilter("ACTIVE") }
        binding.chipInactive.setOnClickListener { viewModel.setStatusFilter("BLOCKED") }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        state.activePeriodText?.let {
                            binding.header.tvSemesterBadge.text = "$it ▾"
                        }
                        binding.progressBar.isVisible = state.isLoading
                        binding.tvEmptyState.isVisible = !state.isLoading && state.users.isEmpty()
                        userAdapter.submitList(state.users)

                        binding.chipActive.text = getString(R.string.chip_active_count, state.activeCount)
                        binding.chipInactive.text = getString(R.string.chip_inactive_count, state.inactiveCount)
                        binding.tvTotal.text = getString(R.string.master_total_count, state.totalCount)

                        val isFilterActive = state.currentFilter.status == "ACTIVE"
                        val isFilterBlocked = state.currentFilter.status == "BLOCKED"
                        binding.chipActive.alpha = if (isFilterBlocked) 0.4f else 1.0f
                        binding.chipInactive.alpha = if (isFilterActive) 0.4f else 1.0f

                        state.errorMessage?.let { error ->
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.userMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUserFormSheet(existing: MasterUser?) {
        val d = DialogAddUserBinding.inflate(layoutInflater)
        val isEdit = existing != null
        if (existing != null) {
            d.etName.setText(existing.name)
            d.etEmail.setText(existing.email)
            d.layoutPassword.isVisible = false
        }

        var selectedRole = existing?.role ?: "STUDENT"
        val roleCards: List<Triple<String, LinearLayout, TextView>> = listOf(
            Triple("STUDENT", d.cardRoleStudent, d.tvRoleStudentTitle),
            Triple("TEACHER", d.cardRoleTeacher, d.tvRoleTeacherTitle),
            Triple("GUARDIAN", d.cardRoleGuardian, d.tvRoleGuardianTitle),
            Triple("ADMIN", d.cardRoleAdmin, d.tvRoleAdminTitle)
        )

        fun renderRoleSelection() {
            roleCards.forEach { (role, card, title) ->
                val active = role == selectedRole
                card.setBackgroundResource(
                    if (active) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
                )
                title.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (active) R.color.agnesian_blue else R.color.agnesian_text
                    )
                )
            }
        }

        roleCards.forEach { (role, card, _) ->
            card.setOnClickListener {
                selectedRole = role
                renderRoleSelection()
            }
        }
        renderRoleSelection()

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(if (isEdit) R.string.dialog_edit_user_title else R.string.dialog_add_user_title),
            subtitle = if (isEdit) "Perbarui identitas atau peran akun" else "Buat akun pengguna baru dengan peran yang sesuai",
            contentView = d.root,
            primaryLabel = if (isEdit) "Simpan" else "Tambah"
        ) {
            d.layoutName.error = null
            d.layoutEmail.error = null
            d.layoutPassword.error = null

            val name = d.etName.text?.toString()?.trim().orEmpty()
            val email = d.etEmail.text?.toString()?.trim().orEmpty()
            val password = d.etPassword.text?.toString().orEmpty()

            var valid = true
            if (name.isBlank()) {
                d.layoutName.error = "Nama lengkap wajib diisi"
                valid = false
            }
            if (email.isBlank()) {
                d.layoutEmail.error = "Email wajib diisi"
                valid = false
            }
            if (!isEdit && password.length < 8) {
                d.layoutPassword.error = "Password minimal 8 karakter"
                valid = false
            }
            if (!valid) return@showAgnesianFormSheet false

            if (existing == null) {
                viewModel.createUser(name, email, password, selectedRole)
            } else {
                viewModel.updateUser(existing, name, email, selectedRole)
            }
            true
        }
    }

    private fun showResetPasswordDialog(user: MasterUser) {
        val d = DialogResetPasswordBinding.inflate(layoutInflater)
        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.dialog_reset_password_title),
            subtitle = "${user.name} (${user.email})",
            contentView = d.root,
            primaryLabel = "Reset Password"
        ) {
            d.layoutNewPassword.error = null
            val newPassword = d.etNewPassword.text?.toString().orEmpty()
            if (newPassword.length < 8) {
                d.layoutNewPassword.error = "Password baru minimal 8 karakter"
                return@showAgnesianFormSheet false
            }
            viewModel.resetPassword(user.id, newPassword)
            true
        }
    }

    private fun confirmToggleStatus(user: MasterUser) {
        val action = if (user.isActive) "memblokir" else "mengaktifkan kembali"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (user.isActive) "Blokir Akun?" else "Buka Blokir Akun?")
            .setMessage("Apakah Anda yakin ingin $action akun milik ${user.name}?")
            .setPositiveButton("Ya") { _, _ -> viewModel.toggleStatus(user) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteUser(user: MasterUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Pengguna?")
            .setMessage("Akun ${user.name} (${user.email}) akan dihapus permanen.\n\nCatatan: Pengguna yang sudah memiliki data profil (siswa/guru/wali) tidak bisa dihapus dan harus diblokir.")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteUser(user.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
