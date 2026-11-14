package id.sch.santaagnes.sim.ui.admin.master.users

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminUserFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminUserViewModel by viewModels()

    private val userAdapter by lazy {
        AdminUserAdapter(
            onEdit = ::showEditUserDialog,
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

        setupRecyclerView()
        setupHeaderAndTabs()
        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = userAdapter
    }

    private fun setupHeaderAndTabs() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAddUser.setOnClickListener {
            showAddUserDialog()
        }

        binding.btnImportUser.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur impor Excel user siap diintegrasikan", Toast.LENGTH_SHORT).show()
        }

        binding.tabStudent.setOnClickListener {
            findNavController().navigate(R.id.action_adminUser_to_adminStudent)
        }

        binding.tabTeacher.setOnClickListener {
            Toast.makeText(requireContext(), "Menuju Master Guru (M1/F-MST-05)", Toast.LENGTH_SHORT).show()
        }

        binding.tabClass.setOnClickListener {
            Toast.makeText(requireContext(), "Menuju Master Kelas (M1/F-MST-07)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFilterAndSearch() {
        val roles = arrayOf("Semua Role", "ADMIN", "TEACHER", "STUDENT", "GUARDIAN")
        binding.spinnerRole.setSimpleItems(roles)
        binding.spinnerRole.setText("Semua Role", false)

        binding.spinnerRole.setOnItemClickListener { _, _, position, _ ->
            val selected = roles[position]
            viewModel.setRoleFilter(if (selected == "Semua Role") null else selected)
        }

        binding.etSearchUser.addTextChangedListener { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }

        binding.chipActive.setOnClickListener {
            viewModel.setStatusFilter("ACTIVE")
        }

        binding.chipInactive.setOnClickListener {
            viewModel.setStatusFilter("BLOCKED")
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state.isLoading
                        binding.tvEmptyState.isVisible = !state.isLoading && state.users.isEmpty()
                        userAdapter.submitList(state.users)

                        binding.chipActive.text = getString(R.string.chip_active_count, state.activeCount)
                        binding.chipInactive.text = getString(R.string.chip_inactive_count, state.inactiveCount)

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

    private fun showAddUserDialog() {
        val dialogBinding = DialogAddUserBinding.inflate(layoutInflater)
        val roles = listOf("STUDENT", "TEACHER", "GUARDIAN", "ADMIN")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roles)
        dialogBinding.spinnerDialogRole.setAdapter(adapter)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Tambah") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val email = dialogBinding.etEmail.text.toString().trim()
                val password = dialogBinding.etPassword.text.toString()
                val role = dialogBinding.spinnerDialogRole.text.toString()

                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                    Toast.makeText(requireContext(), "Semua kolom wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.createUser(name, email, password, role)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditUserDialog(user: MasterUser) {
        val dialogBinding = DialogAddUserBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = getString(R.string.dialog_edit_user_title)
        dialogBinding.etName.setText(user.name)
        dialogBinding.etEmail.setText(user.email)
        dialogBinding.layoutPassword.isVisible = false
        dialogBinding.layoutRole.isVisible = false

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val email = dialogBinding.etEmail.text.toString().trim()
                viewModel.updateUser(user.id, name, email)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showResetPasswordDialog(user: MasterUser) {
        val dialogBinding = DialogResetPasswordBinding.inflate(layoutInflater)
        dialogBinding.tvTargetUser.text = "Pengguna: ${user.name} (${user.email})"

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Reset") { _, _ ->
                val newPassword = dialogBinding.etNewPassword.text.toString()
                if (newPassword.isBlank() || newPassword.length < 8) {
                    Toast.makeText(requireContext(), "Password baru minimal 8 karakter", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.resetPassword(user.id, newPassword)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmToggleStatus(user: MasterUser) {
        val action = if (user.isActive) "memblokir" else "mengaktifkan kembali"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (user.isActive) "Blokir Akun?" else "Buka Blokir Akun?")
            .setMessage("Apakah Anda yakin ingin $action akun milik ${user.name}?")
            .setPositiveButton("Ya") { _, _ ->
                viewModel.toggleStatus(user)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteUser(user: MasterUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Pengguna?")
            .setMessage("Akun ${user.name} (${user.email}) akan dihapus permanen.\n\nCatatan: Sesuai aturan akademik, pengguna yang sudah memiliki data profil (siswa/guru/wali) tidak bisa dihapus dan harus diblokir.")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteUser(user.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
