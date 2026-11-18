package id.sch.santaagnes.sim.ui.admin.master.classes

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
import id.sch.santaagnes.sim.databinding.DialogAddClassBinding
import id.sch.santaagnes.sim.databinding.DialogClassMembersBinding
import id.sch.santaagnes.sim.databinding.DialogHomeroomTeacherBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminClassesBinding
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.ui.admin.master.MasterTab
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.setupMasterTabs
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminClassFragment : Fragment() {

    private var _binding: FragmentAdminClassesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminClassViewModel by viewModels()
    private var renderedYears: List<MasterAcademicYear> = emptyList()

    private val classAdapter by lazy {
        AdminClassAdapter(
            onManageMembers = ::showMembersDialog,
            onSetHomeroom = ::showHomeroomDialog,
            onEdit = ::showEditClassDialog,
            onDelete = ::confirmDeleteClass
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminClassesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nav = findNavController()
        binding.rvClasses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvClasses.adapter = classAdapter

        setupMasterHeader(
            header = binding.header,
            navController = nav,
            titleRes = R.string.title_management_class,
            showImport = true,
            onAdd = ::showAddClassDialog
        )
        setupMasterTabs(binding.tabs, nav, MasterTab.CLASS)
        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupFilterAndSearch() {
        binding.spinnerYear.setSimpleItems(arrayOf(getString(R.string.filter_all_years)))
        binding.spinnerYear.setText(getString(R.string.filter_all_years), false)

        binding.spinnerYear.setOnItemClickListener { _, _, pos, _ ->
            val yearId = if (pos == 0) null else renderedYears.getOrNull(pos - 1)?.id
            viewModel.setYearFilter(yearId)
        }

        binding.etSearchClass.addTextChangedListener { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        state.activePeriodText?.let {
                            binding.header.tvSemesterBadge.text = "$it ▾"
                        }

                        if (state.academicYears != renderedYears) {
                            renderedYears = state.academicYears
                            val labels = buildList {
                                add(getString(R.string.filter_all_years))
                                addAll(state.academicYears.map {
                                    if (it.isActive) "${it.name} (Aktif)" else it.name
                                })
                            }.toTypedArray()
                            binding.spinnerYear.setSimpleItems(labels)
                        }

                        binding.progressBar.isVisible = state.isLoading
                        binding.tvEmptyState.isVisible = !state.isLoading && state.classes.isEmpty()
                        classAdapter.submitList(state.classes)

                        binding.chipTotalClass.text = getString(R.string.chip_class_count, state.totalClasses)
                        binding.chipWithHomeroom.text = getString(R.string.chip_class_with_homeroom, state.withHomeroomCount)
                        binding.chipWithoutHomeroom.text = getString(R.string.chip_class_no_homeroom, state.withoutHomeroomCount)

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

    private fun showAddClassDialog() {
        val state = viewModel.uiState.value
        val years = state.academicYears
        if (years.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Buat Tahun Ajaran terlebih dahulu di menu Tahun Ajaran & Semester.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val d = DialogAddClassBinding.inflate(layoutInflater)
        var selectedYearIdx = years.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
        val yearLabels = years.map { if (it.isActive) "${it.name} (Aktif)" else it.name }.toTypedArray()
        d.spinnerAcademicYear.setSimpleItems(yearLabels)
        d.spinnerAcademicYear.setText(yearLabels[selectedYearIdx], false)
        d.spinnerAcademicYear.setOnItemClickListener { _, _, pos, _ -> selectedYearIdx = pos }

        val grades = arrayOf("10", "11", "12")
        d.spinnerGradeLevel.setSimpleItems(grades)
        d.spinnerGradeLevel.setText("10", false)

        val teachers = state.teachers
        var selectedTeacherIdx = 0
        val teacherLabels = buildList {
            add("Belum diatur")
            addAll(teachers.map { "${it.name} (${it.employeeNumber})" })
        }.toTypedArray()
        d.spinnerHomeroom.setSimpleItems(teacherLabels)
        d.spinnerHomeroom.setText(teacherLabels[0], false)
        d.spinnerHomeroom.setOnItemClickListener { _, _, pos, _ -> selectedTeacherIdx = pos }

        d.etCapacity.setText("36")

        MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Tambah") { _, _ ->
                val year = years.getOrNull(selectedYearIdx) ?: return@setPositiveButton
                val name = d.etClassName.text.toString().trim()
                val grade = d.spinnerGradeLevel.text.toString().trim()
                val cap = d.etCapacity.text.toString().toIntOrNull()
                val teacherId = if (selectedTeacherIdx <= 0) null else teachers.getOrNull(selectedTeacherIdx - 1)?.id

                if (name.isBlank() || grade.isBlank() || cap == null || cap <= 0) {
                    Toast.makeText(requireContext(), "Nama kelas, tingkat, dan kapasitas valid wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createClass(year.id, name, grade, cap, teacherId)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditClassDialog(item: MasterClassGroup) {
        val d = DialogAddClassBinding.inflate(layoutInflater)
        d.tvDialogTitle.setText(R.string.dialog_edit_class_title)
        d.layoutAcademicYear.isVisible = false
        d.layoutHomeroom.isVisible = false
        d.etClassName.setText(item.name)
        d.etCapacity.setText(item.capacity.toString())

        val grades = arrayOf("10", "11", "12")
        d.spinnerGradeLevel.setSimpleItems(grades)
        d.spinnerGradeLevel.setText(item.gradeLevel, false)

        MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = d.etClassName.text.toString().trim()
                val grade = d.spinnerGradeLevel.text.toString().trim()
                val cap = d.etCapacity.text.toString().toIntOrNull()
                if (name.isBlank() || grade.isBlank() || cap == null || cap <= 0) {
                    Toast.makeText(requireContext(), "Data kelas tidak valid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateClass(item.id, name, grade, cap)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showHomeroomDialog(item: MasterClassGroup) {
        val teachers = viewModel.uiState.value.teachers
        val d = DialogHomeroomTeacherBinding.inflate(layoutInflater)
        d.tvClassInfo.text = "Kelas ${item.name} (${item.academicYearName})"

        val labels = buildList {
            add("Lepas Wali Kelas")
            addAll(teachers.map { "${it.name} (${it.employeeNumber})" })
        }.toTypedArray()

        var selectedIdx = if (item.homeroomTeacherId == null) 0 else {
            val found = teachers.indexOfFirst { it.id == item.homeroomTeacherId }
            if (found >= 0) found + 1 else 0
        }
        d.spinnerTeacher.setSimpleItems(labels)
        d.spinnerTeacher.setText(labels[selectedIdx], false)
        d.spinnerTeacher.setOnItemClickListener { _, _, pos, _ -> selectedIdx = pos }

        MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Simpan") { _, _ ->
                val teacherId = if (selectedIdx == 0) null else teachers.getOrNull(selectedIdx - 1)?.id
                viewModel.setHomeroomTeacher(item.id, teacherId)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showMembersDialog(item: MasterClassGroup) {
        val d = DialogClassMembersBinding.inflate(layoutInflater)
        d.tvClassSummary.text = "Kelas ${item.name} · Kapasitas ${item.capacity}"

        var selectedStudentIdx = -1
        var renderedStudents: List<MasterStudent> = emptyList()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setNegativeButton("Tutup", null)
            .create()

        d.btnAddMember.setOnClickListener {
            val student = renderedStudents.getOrNull(selectedStudentIdx)
            if (student == null) {
                Toast.makeText(requireContext(), "Pilih siswa terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addClassMember(item.id, student.id)
        }

        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                d.progressDialog.isVisible = state.isMembersLoading

                if (state.studentsLookup != renderedStudents) {
                    renderedStudents = state.studentsLookup
                    val names = renderedStudents.map { "${it.name} (${it.nis})" }.toTypedArray()
                    d.spinnerStudent.setSimpleItems(names)
                    d.spinnerStudent.setOnItemClickListener { _, _, pos, _ ->
                        selectedStudentIdx = pos
                    }
                }

                val page = state.selectedMembersPage
                if (page != null && page.classId == item.id) {
                    d.tvClassSummary.text = "Kelas ${page.className} · Terisi ${page.members.size} / ${page.capacity}"
                    d.tvMembersEmpty.isVisible = !state.isMembersLoading && page.members.isEmpty()

                    d.containerMembers.removeAllViews()
                    page.members.forEach { member ->
                        val row = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setPadding(0, 8, 0, 8)
                        }
                        val info = TextView(requireContext()).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            text = "${member.name}\nNIS: ${member.nis}"
                            textSize = 13f
                        }
                        val btnMove = MaterialButton(
                            requireContext(),
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle
                        ).apply {
                            text = "Pindah"
                            isAllCaps = false
                            setOnClickListener { showMoveMemberPicker(item, member.studentId, member.name) }
                        }
                        val btnRemove = MaterialButton(
                            requireContext(),
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle
                        ).apply {
                            text = "Hapus"
                            isAllCaps = false
                            setOnClickListener { viewModel.removeClassMember(item.id, member.studentId) }
                        }
                        row.addView(info)
                        row.addView(btnMove)
                        row.addView(btnRemove)
                        d.containerMembers.addView(row)
                    }
                }
            }
        }

        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
        viewModel.loadClassMembers(item.id)
    }

    private fun showMoveMemberPicker(
        sourceClass: MasterClassGroup,
        studentId: Int,
        studentName: String
    ) {
        val targets = viewModel.uiState.value.classes.filter {
            it.id != sourceClass.id && it.academicYearId == sourceClass.academicYearId
        }
        if (targets.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Tidak ada kelas tujuan lain pada tahun ajaran ${sourceClass.academicYearName}.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val names = targets.map { "${it.name} (${it.memberCount}/${it.capacity})" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pindahkan $studentName ke Kelas:")
            .setItems(names) { _, which ->
                viewModel.moveClassMember(sourceClass.id, studentId, targets[which].id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmDeleteClass(item: MasterClassGroup) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Kelas?")
            .setMessage("Kelas ${item.name} (${item.academicYearName}) akan dihapus permanen.\n\nCatatan: Kelas yang sudah memiliki siswa atau penugasan mengajar tidak dapat dihapus.")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteClass(item.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
