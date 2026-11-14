package id.sch.santaagnes.sim.ui.admin.master.students

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import id.sch.santaagnes.sim.databinding.DialogAddStudentBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminStudentsBinding
import id.sch.santaagnes.sim.domain.model.MasterStudent
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminStudentFragment : Fragment() {

    private var _binding: FragmentAdminStudentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminStudentViewModel by viewModels()

    private val studentAdapter by lazy {
        AdminStudentAdapter(
            onEdit = ::showEditStudentDialog,
            onManageGuardians = ::showManageGuardiansDialog,
            onDelete = ::confirmDeleteStudent
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminStudentsBinding.inflate(inflater, container, false)
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
        binding.rvStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStudents.adapter = studentAdapter
    }

    private fun setupHeaderAndTabs() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAddStudent.setOnClickListener {
            showAddStudentDialog()
        }

        binding.btnImportStudent.setOnClickListener {
            Toast.makeText(requireContext(), "Fitur impor Excel siswa siap diintegrasikan", Toast.LENGTH_SHORT).show()
        }

        binding.tabUser.setOnClickListener {
            findNavController().navigate(R.id.action_adminStudent_to_adminUser)
        }

        binding.tabTeacher.setOnClickListener {
            Toast.makeText(requireContext(), "Menuju Master Guru (M1/F-MST-05)", Toast.LENGTH_SHORT).show()
        }

        binding.tabClass.setOnClickListener {
            Toast.makeText(requireContext(), "Menuju Master Kelas (M1/F-MST-07)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFilterAndSearch() {
        val classes = arrayOf("Semua Kelas", "X-1", "XI-1")
        binding.spinnerClass.setSimpleItems(classes)
        binding.spinnerClass.setText("Semua Kelas", false)

        binding.spinnerClass.setOnItemClickListener { _, _, position, _ ->
            val classId = if (position == 0) null else position
            viewModel.setClassFilter(classId)
        }

        binding.etSearchStudent.addTextChangedListener { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state.isLoading
                        binding.tvEmptyState.isVisible = !state.isLoading && state.students.isEmpty()
                        studentAdapter.submitList(state.students)

                        binding.chipTotalStudent.text = getString(R.string.chip_student_count, state.totalCount)
                        binding.chipActive.text = getString(R.string.chip_active_count, state.activeCount)
                        binding.chipInactive.text = getString(R.string.chip_inactive_count, state.inactiveCount)

                        state.errorMessage?.let { error ->
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.studentMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showAddStudentDialog() {
        val dialogBinding = DialogAddStudentBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Tambah") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val nis = dialogBinding.etNis.text.toString().trim()
                val email = dialogBinding.etEmail.text.toString().trim()
                val password = dialogBinding.etPassword.text.toString()
                val phone = dialogBinding.etPhone.text.toString().trim()

                if (name.isBlank() || nis.isBlank() || email.isBlank() || password.isBlank()) {
                    Toast.makeText(requireContext(), "Nama, NIS, Email, dan Password wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.createStudent(name, email, password, nis, phone)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditStudentDialog(student: MasterStudent) {
        val dialogBinding = DialogAddStudentBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = "Ubah Data Siswa"
        dialogBinding.etName.setText(student.name)
        dialogBinding.etNis.setText(student.nis)
        dialogBinding.etPhone.setText(student.phone.orEmpty())
        dialogBinding.layoutEmail.isVisible = false
        dialogBinding.layoutPassword.isVisible = false

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val nis = dialogBinding.etNis.text.toString().trim()
                val phone = dialogBinding.etPhone.text.toString().trim()

                viewModel.updateStudent(student.id, name, nis, phone)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showManageGuardiansDialog(student: MasterStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Relasi Wali Murid")
            .setMessage("Siswa ${student.name} saat ini memiliki ${student.guardianCount} akun wali murid terhubung.")
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun confirmDeleteStudent(student: MasterStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Siswa?")
            .setMessage("Data siswa ${student.name} (NIS: ${student.nis}) akan dihapus permanen.\n\nCatatan: Sesuai aturan akademik, siswa yang sudah memiliki data kelas/nilai/absensi tidak bisa dihapus.")
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.deleteStudent(student.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
