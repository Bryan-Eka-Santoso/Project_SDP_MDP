package id.sch.santaagnes.sim.ui.admin.master.teachers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import id.sch.santaagnes.sim.databinding.DialogAddAssignmentBinding
import id.sch.santaagnes.sim.databinding.DialogAddTeacherBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminTeachersBinding
import id.sch.santaagnes.sim.domain.model.MasterTeacher
import id.sch.santaagnes.sim.domain.model.MasterTeachingAssignment
import id.sch.santaagnes.sim.ui.admin.master.AgnesianOption
import id.sch.santaagnes.sim.ui.admin.master.MasterTab
import id.sch.santaagnes.sim.ui.admin.master.bindFilterPill
import id.sch.santaagnes.sim.ui.admin.master.bindSelectorField
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.setupMasterTabs
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminTeacherFragment : Fragment() {

    private var _binding: FragmentAdminTeachersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminTeacherViewModel by viewModels()

    private val teacherTypeOptions by lazy {
        listOf(
            AgnesianOption("", getString(R.string.filter_all_teachers), "Tampilkan wali kelas & guru biasa"),
            AgnesianOption("HOMEROOM", getString(R.string.filter_homeroom_teachers), "Akses mengajar + pantau kelas binaan"),
            AgnesianOption("REGULAR", getString(R.string.filter_regular_teachers), "Hanya mengajar jadwal & mapel diampu")
        )
    }

    private val teacherAdapter by lazy {
        AdminTeacherAdapter(
            onViewAssignments = { teacher ->
                viewModel.setMode(TeacherSectionMode.ASSIGNMENTS, teacher)
            },
            onEdit = { showTeacherFormSheet(it) },
            onDelete = ::confirmDeleteTeacher
        )
    }

    private val assignmentAdapter by lazy {
        AdminTeachingAssignmentAdapter(
            onEditTeacher = ::showEditAssignmentDialog,
            onDelete = ::confirmDeleteAssignment
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminTeachersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nav = findNavController()
        binding.rvTeachers.layoutManager = LinearLayoutManager(requireContext())

        setupMasterHeader(
            header = binding.header,
            navController = nav,
            titleRes = R.string.title_management_teacher,
            showImport = true,
            onAdd = {
                if (viewModel.uiState.value.mode == TeacherSectionMode.TEACHERS) {
                    showTeacherFormSheet(null)
                } else {
                    showAddAssignmentDialog()
                }
            }
        )
        setupMasterTabs(binding.tabs, nav, MasterTab.TEACHER)
        setupModeSwitcher()
        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupModeSwitcher() {
        binding.modeTeachers.setOnClickListener { viewModel.setMode(TeacherSectionMode.TEACHERS, null) }
        binding.modeAssignments.setOnClickListener { viewModel.setMode(TeacherSectionMode.ASSIGNMENTS, null) }
    }

    private fun setupFilterAndSearch() {
        binding.btnFilterTeacherType.text = teacherTypeOptions.first().title
        bindFilterPill(
            pill = binding.btnFilterTeacherType,
            sheetTitle = "Filter Jenis Akses Guru",
            options = { teacherTypeOptions },
            selectedIndex = {
                when (viewModel.uiState.value.homeroomFilter) {
                    true -> 1
                    false -> 2
                    null -> 0
                }
            },
            onSelected = { idx, _ ->
                val filter = when (idx) {
                    1 -> true
                    2 -> false
                    else -> null
                }
                viewModel.setHomeroomFilter(filter)
            }
        )

        binding.etSearchTeacher.addTextChangedListener { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }

        binding.chipHomeroom.setOnClickListener { viewModel.setHomeroomFilter(true) }
        binding.chipRegular.setOnClickListener { viewModel.setHomeroomFilter(false) }
        binding.tvSelectedTeacherChip.setOnClickListener { viewModel.clearTeacherAssignmentFilter() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        state.activePeriodText?.let {
                            binding.header.tvSemesterBadge.text = "$it ▾"
                        }

                        val isTeachersMode = state.mode == TeacherSectionMode.TEACHERS
                        binding.modeTeachers.setBackgroundResource(
                            if (isTeachersMode) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive
                        )
                        binding.modeTeachers.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (isTeachersMode) R.color.white else R.color.agnesian_blue
                            )
                        )
                        binding.modeAssignments.setBackgroundResource(
                            if (!isTeachersMode) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive
                        )
                        binding.modeAssignments.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (!isTeachersMode) R.color.white else R.color.agnesian_blue
                            )
                        )

                        binding.header.btnImport.isVisible = isTeachersMode
                        binding.btnFilterTeacherType.isVisible = isTeachersMode
                        binding.layoutTeacherCounters.isVisible = isTeachersMode
                        binding.etSearchTeacher.setHint(
                            if (isTeachersMode) R.string.search_teacher_hint else R.string.search_assignment_hint
                        )

                        val selectedTeacher = state.selectedTeacherForAssignments
                        binding.tvSelectedTeacherChip.isVisible = !isTeachersMode && selectedTeacher != null
                        if (selectedTeacher != null) {
                            binding.tvSelectedTeacherChip.text = "Guru: ${selectedTeacher.name}  ✕"
                        }

                        binding.progressBar.isVisible = state.isLoading

                        if (isTeachersMode) {
                            if (binding.rvTeachers.adapter !== teacherAdapter) {
                                binding.rvTeachers.adapter = teacherAdapter
                            }
                            teacherAdapter.submitList(state.teachers)
                            binding.tvEmptyState.setText(R.string.teacher_empty)
                            binding.tvEmptyState.isVisible = !state.isLoading && state.teachers.isEmpty()

                            binding.chipTotalTeacher.text = getString(R.string.chip_teacher_count, state.totalTeachers)
                            binding.chipHomeroom.text = getString(R.string.chip_homeroom_count, state.homeroomCount)
                            binding.chipRegular.text = getString(R.string.chip_regular_count, state.regularCount)

                            binding.chipHomeroom.alpha = if (state.homeroomFilter == false) 0.4f else 1.0f
                            binding.chipRegular.alpha = if (state.homeroomFilter == true) 0.4f else 1.0f
                        } else {
                            if (binding.rvTeachers.adapter !== assignmentAdapter) {
                                binding.rvTeachers.adapter = assignmentAdapter
                            }
                            assignmentAdapter.submitList(state.assignments)
                            binding.tvEmptyState.setText(R.string.assignment_empty)
                            binding.tvEmptyState.isVisible = !state.isLoading && state.assignments.isEmpty()
                        }

                        state.errorMessage?.let { error ->
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.message.collect { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showTeacherFormSheet(existing: MasterTeacher?) {
        val d = DialogAddTeacherBinding.inflate(layoutInflater)
        val isEdit = existing != null
        if (existing != null) {
            d.etName.setText(existing.name)
            d.etNip.setText(existing.employeeNumber)
            d.etEmail.setText(existing.email)
            d.layoutPassword.isVisible = false
        }

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(if (isEdit) R.string.dialog_edit_teacher_title else R.string.dialog_add_teacher_title),
            subtitle = if (isEdit) "Perbarui profil & akun guru" else "Tambahkan guru pengajar baru",
            contentView = d.root,
            primaryLabel = if (isEdit) "Simpan" else "Tambah"
        ) {
            val name = d.etName.text?.toString()?.trim().orEmpty()
            val nip = d.etNip.text?.toString()?.trim().orEmpty()
            val email = d.etEmail.text?.toString()?.trim().orEmpty()
            val pwd = d.etPassword.text?.toString().orEmpty()
            if (name.isBlank() || nip.isBlank() || email.isBlank() || (!isEdit && pwd.length < 8)) {
                Toast.makeText(
                    requireContext(),
                    if (isEdit) "Nama, NIP, dan Email wajib diisi" else "Nama, NIP, Email, dan Password (min. 8 karakter) wajib diisi",
                    Toast.LENGTH_SHORT
                ).show()
                return@showAgnesianFormSheet false
            }
            if (existing == null) {
                viewModel.createTeacher(name, email, pwd, nip)
            } else {
                viewModel.updateTeacher(existing.id, name, email, nip)
            }
            true
        }
    }

    private fun confirmDeleteTeacher(teacher: MasterTeacher) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Guru?")
            .setMessage("Data guru ${teacher.name} (NIP: ${teacher.employeeNumber}) akan dihapus permanen.\n\nCatatan: Guru yang sudah menjadi wali kelas atau memiliki penugasan/riwayat mengajar tidak bisa dihapus dan harus diblokir.")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteTeacher(teacher.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAddAssignmentDialog() {
        val state = viewModel.uiState.value
        val teachers = state.allTeachersLookup.ifEmpty { state.teachers }
        val subjects = state.subjects
        val classes = state.classes

        if (teachers.isEmpty() || subjects.isEmpty() || classes.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Pastikan data Guru, Mata Pelajaran, dan Kelas sudah tersedia terlebih dahulu.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val d = DialogAddAssignmentBinding.inflate(layoutInflater)
        d.tvSemesterInfo.text = state.activePeriodText ?: "Semester Aktif"

        var selectedTeacherIdx = state.selectedTeacherForAssignments?.let { sel ->
            teachers.indexOfFirst { it.id == sel.id }
        } ?: -1
        var selectedSubjectIdx = -1
        var selectedClassIdx = -1

        val teacherOptions = teachers.map { AgnesianOption(it.id.toString(), it.name, "NIP: ${it.employeeNumber}") }
        if (selectedTeacherIdx >= 0) {
            d.etTeacherSelector.setText(teacherOptions[selectedTeacherIdx].title)
        }
        bindSelectorField(
            layout = d.layoutTeacher,
            input = d.etTeacherSelector,
            sheetTitle = "Pilih Guru Pengajar",
            options = { teacherOptions },
            selectedIndex = { selectedTeacherIdx },
            onSelected = { idx, _ -> selectedTeacherIdx = idx }
        )

        val subjectOptions = subjects.map { AgnesianOption(it.id.toString(), it.name, "Kode: ${it.code} · ${it.type}") }
        bindSelectorField(
            layout = d.layoutSubject,
            input = d.etSubjectSelector,
            sheetTitle = "Pilih Mata Pelajaran",
            options = { subjectOptions },
            selectedIndex = { selectedSubjectIdx },
            onSelected = { idx, _ -> selectedSubjectIdx = idx }
        )

        val classOptions = classes.map { AgnesianOption(it.id.toString(), it.name, "Tingkat ${it.gradeLevel}") }
        bindSelectorField(
            layout = d.layoutClass,
            input = d.etClassSelector,
            sheetTitle = "Pilih Kelas",
            options = { classOptions },
            selectedIndex = { selectedClassIdx },
            onSelected = { idx, _ -> selectedClassIdx = idx }
        )

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.dialog_add_assignment_title),
            subtitle = "Tetapkan guru mengajar mapel & kelas di semester aktif",
            contentView = d.root,
            primaryLabel = "Tambah"
        ) {
            val t = teachers.getOrNull(selectedTeacherIdx)
            val s = subjects.getOrNull(selectedSubjectIdx)
            val c = classes.getOrNull(selectedClassIdx)
            if (t == null || s == null || c == null) {
                Toast.makeText(requireContext(), "Pilih guru, mata pelajaran, dan kelas", Toast.LENGTH_SHORT).show()
                return@showAgnesianFormSheet false
            }
            viewModel.createTeachingAssignment(t.id, s.id, c.id)
            true
        }
    }

    private fun showEditAssignmentDialog(item: MasterTeachingAssignment) {
        val state = viewModel.uiState.value
        val teachers = state.allTeachersLookup.ifEmpty { state.teachers }
        if (teachers.isEmpty()) return

        val d = DialogAddAssignmentBinding.inflate(layoutInflater)
        d.tvSemesterInfo.text = "${item.subjectName} · Kelas ${item.className}"
        d.layoutSubject.isVisible = false
        d.layoutClass.isVisible = false

        var selectedTeacherIdx = teachers.indexOfFirst { it.id == item.teacherId }
        val teacherOptions = teachers.map { AgnesianOption(it.id.toString(), it.name, "NIP: ${it.employeeNumber}") }
        if (selectedTeacherIdx >= 0) {
            d.etTeacherSelector.setText(teacherOptions[selectedTeacherIdx].title)
        }
        bindSelectorField(
            layout = d.layoutTeacher,
            input = d.etTeacherSelector,
            sheetTitle = "Ganti Guru Pengajar",
            options = { teacherOptions },
            selectedIndex = { selectedTeacherIdx },
            onSelected = { idx, _ -> selectedTeacherIdx = idx }
        )

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.dialog_edit_assignment_title),
            subtitle = "${item.subjectName} · Kelas ${item.className}",
            contentView = d.root,
            primaryLabel = "Simpan"
        ) {
            val t = teachers.getOrNull(selectedTeacherIdx)
            if (t == null) {
                Toast.makeText(requireContext(), "Pilih guru pengajar terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@showAgnesianFormSheet false
            }
            viewModel.updateTeachingAssignmentTeacher(item.id, t.id)
            true
        }
    }

    private fun confirmDeleteAssignment(item: MasterTeachingAssignment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Penugasan?")
            .setMessage("Hapus penugasan ${item.teacherName} untuk ${item.subjectName} di kelas ${item.className}?")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteTeachingAssignment(item.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
