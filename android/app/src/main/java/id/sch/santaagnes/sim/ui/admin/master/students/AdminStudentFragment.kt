package id.sch.santaagnes.sim.ui.admin.master.students

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.DialogAddStudentBinding
import id.sch.santaagnes.sim.databinding.DialogStudentGuardiansBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminStudentsBinding
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.ui.admin.master.MasterTab
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.setupMasterTabs
import id.sch.santaagnes.sim.ui.admin.master.showIsoDatePicker
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminStudentFragment : Fragment() {

    private var _binding: FragmentAdminStudentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminStudentViewModel by viewModels()
    private var renderedClasses: List<MasterClassGroup> = emptyList()

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

        val nav = findNavController()
        binding.rvStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStudents.adapter = studentAdapter

        setupMasterHeader(
            header = binding.header,
            navController = nav,
            titleRes = R.string.title_management_student,
            showImport = true,
            onAdd = ::showAddStudentDialog
        )
        setupMasterTabs(binding.tabs, nav, MasterTab.STUDENT)
        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupFilterAndSearch() {
        binding.spinnerClass.setSimpleItems(arrayOf(getString(R.string.filter_all_classes)))
        binding.spinnerClass.setText(getString(R.string.filter_all_classes), false)

        binding.spinnerClass.setOnItemClickListener { _, _, position, _ ->
            val classId = if (position == 0) null else renderedClasses.getOrNull(position - 1)?.id
            viewModel.setClassFilter(classId)
        }

        binding.etSearchStudent.addTextChangedListener { editable ->
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

                        if (state.classes != renderedClasses) {
                            renderedClasses = state.classes
                            val labels = buildList {
                                add(getString(R.string.filter_all_classes))
                                addAll(state.classes.map { it.name })
                            }.toTypedArray()
                            binding.spinnerClass.setSimpleItems(labels)
                        }

                        binding.progressBar.isVisible = state.isLoading
                        binding.tvEmptyState.isVisible = !state.isLoading && state.students.isEmpty()
                        studentAdapter.submitList(state.students)

                        binding.chipTotalStudent.text = getString(R.string.chip_student_count, state.totalCount)
                        binding.chipActive.text = getString(R.string.chip_active_count, state.activeCount)
                        binding.chipInactive.text = getString(R.string.chip_inactive_count, state.inactiveCount)

                        binding.chipActive.alpha = if (state.statusFilter == "BLOCKED") 0.4f else 1.0f
                        binding.chipInactive.alpha = if (state.statusFilter == "ACTIVE") 0.4f else 1.0f

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
        dialogBinding.etBirthDate.setOnClickListener {
            showIsoDatePicker(requireContext(), dialogBinding.etBirthDate.text?.toString()) {
                dialogBinding.etBirthDate.setText(it)
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Tambah") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val nis = dialogBinding.etNis.text.toString().trim()
                val birthDate = dialogBinding.etBirthDate.text.toString().trim()
                val email = dialogBinding.etEmail.text.toString().trim()
                val password = dialogBinding.etPassword.text.toString()
                val phone = dialogBinding.etPhone.text.toString().trim()

                if (name.isBlank() || nis.isBlank() || birthDate.isBlank() || email.isBlank() || password.isBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Nama, NIS, Tanggal Lahir, Email, dan Password wajib diisi",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                viewModel.createStudent(name, email, password, nis, birthDate, phone)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditStudentDialog(student: MasterStudent) {
        val dialogBinding = DialogAddStudentBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = "Ubah Data Siswa"
        dialogBinding.etName.setText(student.name)
        dialogBinding.etNis.setText(student.nis)
        dialogBinding.etBirthDate.setText(student.birthDate.orEmpty())
        dialogBinding.etEmail.setText(student.email)
        dialogBinding.etPhone.setText(student.phone.orEmpty())
        dialogBinding.layoutPassword.isVisible = false

        dialogBinding.etBirthDate.setOnClickListener {
            showIsoDatePicker(requireContext(), dialogBinding.etBirthDate.text?.toString()) {
                dialogBinding.etBirthDate.setText(it)
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val email = dialogBinding.etEmail.text.toString().trim()
                val nis = dialogBinding.etNis.text.toString().trim()
                val birthDate = dialogBinding.etBirthDate.text.toString().trim()
                val phone = dialogBinding.etPhone.text.toString().trim()

                if (name.isBlank() || email.isBlank() || nis.isBlank() || birthDate.isBlank()) {
                    Toast.makeText(requireContext(), "Nama, Email, NIS, dan Tanggal Lahir wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateStudent(student.id, name, email, nis, birthDate, phone)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showManageGuardiansDialog(student: MasterStudent) {
        val dialogBinding = DialogStudentGuardiansBinding.inflate(layoutInflater)
        dialogBinding.tvStudentHeader.text = "${student.name} (NIS: ${student.nis})"

        var selectedGuardianIndex = -1
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton("Tutup", null)
            .create()

        dialogBinding.btnToggleCreateGuardian.setOnClickListener {
            dialogBinding.layoutCreateGuardian.isVisible = !dialogBinding.layoutCreateGuardian.isVisible
        }

        dialogBinding.btnLinkExisting.setOnClickListener {
            val guardians = viewModel.uiState.value.guardians
            val guardian = guardians.getOrNull(selectedGuardianIndex)
            val relationship = dialogBinding.etRelationship.text.toString().trim()
            if (guardian == null || relationship.isBlank()) {
                Toast.makeText(requireContext(), "Pilih wali murid dan isi hubungan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.linkGuardian(student.id, guardian.id, relationship)
            dialogBinding.etRelationship.setText("")
        }

        dialogBinding.btnCreateAndLinkGuardian.setOnClickListener {
            val gName = dialogBinding.etGuardianName.text.toString().trim()
            val gEmail = dialogBinding.etGuardianEmail.text.toString().trim()
            val gPwd = dialogBinding.etGuardianPassword.text.toString()
            val gPhone = dialogBinding.etGuardianPhone.text.toString().trim()
            val rel = dialogBinding.etNewGuardianRelationship.text.toString().trim()
            if (gName.isBlank() || gEmail.isBlank() || gPwd.length < 8 || gPhone.isBlank() || rel.isBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Semua data wali murid dan hubungan wajib diisi (password min. 8 karakter)",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            viewModel.createAndLinkGuardian(student.id, gName, gEmail, gPwd, gPhone, rel)
            dialogBinding.etGuardianName.setText("")
            dialogBinding.etGuardianEmail.setText("")
            dialogBinding.etGuardianPassword.setText("")
            dialogBinding.etGuardianPhone.setText("")
            dialogBinding.etNewGuardianRelationship.setText("")
            dialogBinding.layoutCreateGuardian.isVisible = false
        }

        var renderedGuardians = emptyList<id.sch.santaagnes.sim.domain.model.MasterGuardian>()
        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                dialogBinding.progressDialog.isVisible = state.isGuardianLoading
                dialogBinding.tvLinkedEmpty.isVisible =
                    !state.isGuardianLoading && state.selectedStudentGuardians.isEmpty()

                if (state.guardians != renderedGuardians) {
                    renderedGuardians = state.guardians
                    val guardianNames = state.guardians.map { "${it.name} (${it.email})" }.toTypedArray()
                    dialogBinding.spinnerGuardian.setSimpleItems(guardianNames)
                    dialogBinding.spinnerGuardian.setOnItemClickListener { _, _, position, _ ->
                        selectedGuardianIndex = position
                    }
                }

                dialogBinding.containerLinkedGuardians.removeAllViews()
                state.selectedStudentGuardians.forEach { link ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 8, 0, 8)
                    }
                    val info = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        text = "${link.name} (${link.relationship})\n${link.phone ?: link.email}"
                        textSize = 13f
                    }
                    val btnRemove = MaterialButton(
                        requireContext(),
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = "Putus"
                        isAllCaps = false
                        setOnClickListener { viewModel.unlinkGuardian(student.id, link.guardianId) }
                    }
                    row.addView(info)
                    row.addView(btnRemove)
                    dialogBinding.containerLinkedGuardians.addView(row)
                }
            }
        }

        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
        viewModel.loadGuardianPanel(student.id)
    }

    private fun confirmDeleteStudent(student: MasterStudent) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Siswa?")
            .setMessage("Data siswa ${student.name} (NIS: ${student.nis}) akan dihapus permanen.\n\nCatatan: Siswa yang sudah memiliki data kelas/nilai/absensi tidak bisa dihapus.")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteStudent(student.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
