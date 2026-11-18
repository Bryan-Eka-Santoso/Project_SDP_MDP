package id.sch.santaagnes.sim.ui.admin.master.students

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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.DialogAddStudentBinding
import id.sch.santaagnes.sim.databinding.DialogStudentClassBinding
import id.sch.santaagnes.sim.databinding.DialogStudentGuardiansBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminStudentsBinding
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterGuardian
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.ui.admin.master.AgnesianOption
import id.sch.santaagnes.sim.ui.admin.master.MasterTab
import id.sch.santaagnes.sim.ui.admin.master.bindDateField
import id.sch.santaagnes.sim.ui.admin.master.bindFilterPill
import id.sch.santaagnes.sim.ui.admin.master.bindSelectorField
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.setupMasterTabs
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminStudentFragment : Fragment() {

    private var _binding: FragmentAdminStudentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminStudentViewModel by viewModels()
    private var hasStartedOnce = false

    private val studentAdapter by lazy {
        AdminStudentAdapter(
            onAssignClass = ::showAssignClassDialog,
            onEdit = { showStudentFormSheet(it) },
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
            onAdd = { showStudentFormSheet(null) }
        )
        setupMasterTabs(binding.tabs, nav, MasterTab.STUDENT)
        setupFilterAndSearch()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        if (hasStartedOnce) {
            viewModel.refreshAll()
        }
        hasStartedOnce = true
    }

    private fun setupFilterAndSearch() {
        binding.btnFilterClass.text = getString(R.string.filter_all_classes)
        bindFilterPill(
            pill = binding.btnFilterClass,
            sheetTitle = "Filter Kelas Siswa",
            options = {
                buildList {
                    add(AgnesianOption("", getString(R.string.filter_all_classes), "Tampilkan seluruh siswa"))
                    addAll(
                        viewModel.uiState.value.classes.map { cls ->
                            AgnesianOption(
                                id = cls.id.toString(),
                                title = cls.name,
                                subtitle = "Tingkat ${cls.gradeLevel} · ${cls.memberCount}/${cls.capacity} siswa"
                            )
                        }
                    )
                }
            },
            selectedIndex = {
                val activeId = viewModel.uiState.value.currentFilter.classGroupId
                if (activeId == null) 0 else {
                    val idx = viewModel.uiState.value.classes.indexOfFirst { it.id == activeId }
                    if (idx >= 0) idx + 1 else 0
                }
            },
            onSelected = { _, option ->
                viewModel.setClassFilter(option.id.toIntOrNull())
            }
        )

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

    private fun selectableActiveClasses(): List<MasterClassGroup> {
        val all = viewModel.uiState.value.classes
        return all.filter { it.isAcademicYearActive }.ifEmpty { all }
    }

    private fun setupClassSelector(
        layout: TextInputLayout,
        input: TextInputEditText,
        currentClassId: Int?
    ): () -> Int? {
        val selectable = selectableActiveClasses()
        val options = buildList {
            add(AgnesianOption("", getString(R.string.student_class_none), "Siswa belum masuk kelas"))
            addAll(
                selectable.map { cls ->
                    AgnesianOption(
                        id = cls.id.toString(),
                        title = cls.name,
                        subtitle = "Tingkat ${cls.gradeLevel} · Terisi ${cls.memberCount}/${cls.capacity}"
                    )
                }
            )
        }

        var selectedIdx = currentClassId?.let { id ->
            val found = selectable.indexOfFirst { it.id == id }
            if (found >= 0) found + 1 else 0
        } ?: 0
        var selectedClassId = if (selectedIdx > 0) selectable[selectedIdx - 1].id else null
        input.setText(options[selectedIdx].title)

        bindSelectorField(
            layout = layout,
            input = input,
            sheetTitle = "Pilih Kelas Siswa",
            subtitle = "Kelas pada tahun ajaran aktif",
            options = { options },
            selectedIndex = { selectedIdx },
            onSelected = { idx, _ ->
                selectedIdx = idx
                selectedClassId = if (idx == 0) null else selectable.getOrNull(idx - 1)?.id
            }
        )
        return { selectedClassId }
    }

    private fun showAssignClassDialog(student: MasterStudent) {
        val d = DialogStudentClassBinding.inflate(layoutInflater)
        val currentLabel = student.className ?: getString(R.string.student_class_none)
        d.tvCurrentClass.text = "Kelas saat ini: $currentLabel"
        val getSelectedClassId = setupClassSelector(d.layoutClass, d.etClassSelector, student.classId)

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.student_class_action),
            subtitle = "${student.name} (NIS: ${student.nis})",
            contentView = d.root,
            primaryLabel = "Simpan Kelas"
        ) {
            viewModel.assignStudentClass(student, getSelectedClassId())
            true
        }
    }

    private fun showStudentFormSheet(existing: MasterStudent?) {
        val d = DialogAddStudentBinding.inflate(layoutInflater)
        val isEdit = existing != null
        if (existing != null) {
            d.etName.setText(existing.name)
            d.etNis.setText(existing.nis)
            d.etBirthDate.setText(existing.birthDate.orEmpty())
            d.etEmail.setText(existing.email)
            d.etPhone.setText(existing.phone.orEmpty())
            d.layoutPassword.isVisible = false
        }

        bindDateField(d.layoutBirthDate, d.etBirthDate, childFragmentManager, "Pilih Tanggal Lahir")
        val getSelectedClassId = setupClassSelector(d.layoutClass, d.etClassSelector, existing?.classId)

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(if (isEdit) R.string.dialog_edit_student_title else R.string.dialog_add_student_title),
            subtitle = if (isEdit) "Perbarui profil siswa atau pindahkan kelas" else "Daftarkan akun siswa baru",
            contentView = d.root,
            primaryLabel = if (isEdit) "Simpan" else "Tambah"
        ) {
            d.layoutName.error = null
            d.layoutNis.error = null
            d.layoutBirthDate.error = null
            d.layoutEmail.error = null
            d.layoutPassword.error = null

            val name = d.etName.text?.toString()?.trim().orEmpty()
            val nis = d.etNis.text?.toString()?.trim().orEmpty()
            val birthDate = d.etBirthDate.text?.toString()?.trim().orEmpty()
            val email = d.etEmail.text?.toString()?.trim().orEmpty()
            val password = d.etPassword.text?.toString().orEmpty()
            val phone = d.etPhone.text?.toString()?.trim().orEmpty()

            var valid = true
            if (name.isBlank()) {
                d.layoutName.error = "Nama siswa wajib diisi"
                valid = false
            }
            if (nis.isBlank()) {
                d.layoutNis.error = "NIS wajib diisi"
                valid = false
            }
            if (birthDate.isBlank()) {
                d.layoutBirthDate.error = "Tanggal lahir wajib dipilih"
                valid = false
            }
            if (email.isBlank()) {
                d.layoutEmail.error = "Email akun wajib diisi"
                valid = false
            }
            if (!isEdit && password.length < 8) {
                d.layoutPassword.error = "Password minimal 8 karakter"
                valid = false
            }
            if (!valid) return@showAgnesianFormSheet false

            if (existing == null) {
                viewModel.createStudent(
                    name = name,
                    email = email,
                    password = password,
                    nis = nis,
                    birthDate = birthDate,
                    phone = phone,
                    classGroupId = getSelectedClassId()
                )
            } else {
                viewModel.updateStudent(
                    id = existing.id,
                    name = name,
                    email = email,
                    nis = nis,
                    birthDate = birthDate,
                    phone = phone,
                    currentClassId = existing.classId,
                    targetClassId = getSelectedClassId()
                )
            }
            true
        }
    }

    private fun showManageGuardiansDialog(student: MasterStudent) {
        val d = DialogStudentGuardiansBinding.inflate(layoutInflater)
        var selectedGuardian: MasterGuardian? = null

        val sheet = showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.student_guardians_action),
            subtitle = "${student.name} (NIS: ${student.nis})",
            contentView = d.root,
            secondaryLabel = "Tutup",
            showPrimary = false,
            onPrimary = { true }
        )

        bindSelectorField(
            layout = d.layoutGuardian,
            input = d.etGuardianSelector,
            sheetTitle = "Pilih Wali Murid",
            options = {
                viewModel.uiState.value.guardians.map { g ->
                    AgnesianOption(g.id.toString(), g.name, "${g.email} · ${g.phone}")
                }
            },
            selectedIndex = {
                val id = selectedGuardian?.id
                viewModel.uiState.value.guardians.indexOfFirst { it.id == id }
            },
            onSelected = { idx, _ ->
                selectedGuardian = viewModel.uiState.value.guardians.getOrNull(idx)
            }
        )

        d.btnToggleCreateGuardian.setOnClickListener {
            d.layoutCreateGuardian.isVisible = !d.layoutCreateGuardian.isVisible
        }

        d.btnLinkExisting.setOnClickListener {
            val guardian = selectedGuardian
            val relationship = d.etRelationship.text?.toString()?.trim().orEmpty()
            if (guardian == null || relationship.isBlank()) {
                Toast.makeText(requireContext(), "Pilih wali murid dan isi hubungan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.linkGuardian(student.id, guardian.id, relationship)
            d.etRelationship.setText("")
        }

        d.btnCreateAndLinkGuardian.setOnClickListener {
            val gName = d.etGuardianName.text?.toString()?.trim().orEmpty()
            val gEmail = d.etGuardianEmail.text?.toString()?.trim().orEmpty()
            val gPwd = d.etGuardianPassword.text?.toString().orEmpty()
            val gPhone = d.etGuardianPhone.text?.toString()?.trim().orEmpty()
            val rel = d.etNewGuardianRelationship.text?.toString()?.trim().orEmpty()
            if (gName.isBlank() || gEmail.isBlank() || gPwd.length < 8 || gPhone.isBlank() || rel.isBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Semua data wali murid dan hubungan wajib diisi (password min. 8 karakter)",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            viewModel.createAndLinkGuardian(student.id, gName, gEmail, gPwd, gPhone, rel)
            d.etGuardianName.setText("")
            d.etGuardianEmail.setText("")
            d.etGuardianPassword.setText("")
            d.etGuardianPhone.setText("")
            d.etNewGuardianRelationship.setText("")
            d.layoutCreateGuardian.isVisible = false
        }

        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                d.progressDialog.isVisible = state.isGuardianLoading
                d.tvLinkedEmpty.isVisible =
                    !state.isGuardianLoading && state.selectedStudentGuardians.isEmpty()

                d.containerLinkedGuardians.removeAllViews()
                state.selectedStudentGuardians.forEach { link ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.bg_option_unselected)
                        setPadding(24, 18, 24, 18)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12 }
                    }
                    val info = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        text = "${link.name} (${link.relationship})\n${link.phone ?: link.email}"
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.agnesian_text))
                        textSize = 13f
                    }
                    val btnRemove = MaterialButton(
                        requireContext(),
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = "Putus"
                        isAllCaps = false
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.change_cancel))
                        strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.change_cancel)
                        setOnClickListener { viewModel.unlinkGuardian(student.id, link.guardianId) }
                    }
                    row.addView(info)
                    row.addView(btnRemove)
                    d.containerLinkedGuardians.addView(row)
                }
            }
        }

        sheet.setOnDismissListener { job.cancel() }
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
