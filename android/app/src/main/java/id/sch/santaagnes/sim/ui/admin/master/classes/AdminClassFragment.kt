package id.sch.santaagnes.sim.ui.admin.master.classes

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
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.DialogAddClassBinding
import id.sch.santaagnes.sim.databinding.DialogClassMembersBinding
import id.sch.santaagnes.sim.databinding.DialogHomeroomTeacherBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminClassesBinding
import id.sch.santaagnes.sim.domain.model.MasterClassGroup
import id.sch.santaagnes.sim.domain.model.MasterStudent
import id.sch.santaagnes.sim.ui.admin.master.AgnesianOption
import id.sch.santaagnes.sim.ui.admin.master.MasterTab
import id.sch.santaagnes.sim.ui.admin.master.bindFilterPill
import id.sch.santaagnes.sim.ui.admin.master.bindSelectorField
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.setupMasterTabs
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianOptionSheet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminClassFragment : Fragment() {

    private var _binding: FragmentAdminClassesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminClassViewModel by viewModels()

    private val classAdapter by lazy {
        AdminClassAdapter(
            onManageMembers = ::showMembersDialog,
            onSetHomeroom = ::showHomeroomDialog,
            onEdit = { showClassFormSheet(it) },
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
            // Server belum punya impor kelas: tombol memberi tahu hal itu (tanpa onImport).
            showImport = true,
            onAdd = { showClassFormSheet(null) }
        )
        setupMasterTabs(binding.tabs, nav, MasterTab.CLASS)
        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupFilterAndSearch() {
        binding.btnFilterYear.text = getString(R.string.filter_all_years)
        bindFilterPill(
            pill = binding.btnFilterYear,
            sheetTitle = "Filter Tahun Ajaran",
            options = {
                buildList {
                    add(AgnesianOption("", getString(R.string.filter_all_years), "Tampilkan kelas semua periode"))
                    addAll(
                        viewModel.uiState.value.academicYears.map { yr ->
                            AgnesianOption(
                                id = yr.id.toString(),
                                title = if (yr.isActive) "${yr.name} (Aktif)" else yr.name,
                                subtitle = "${yr.startDate} s.d. ${yr.endDate}"
                            )
                        }
                    )
                }
            },
            selectedIndex = {
                val activeId = viewModel.uiState.value.currentFilter.academicYearId
                if (activeId == null) 0 else {
                    val idx = viewModel.uiState.value.academicYears.indexOfFirst { it.id == activeId }
                    if (idx >= 0) idx + 1 else 0
                }
            },
            onSelected = { _, option ->
                viewModel.setYearFilter(option.id.toIntOrNull())
            }
        )

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

    private fun showClassFormSheet(existing: MasterClassGroup?) {
        val state = viewModel.uiState.value
        val years = state.academicYears
        if (existing == null && years.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Buat Tahun Ajaran terlebih dahulu di menu Tahun Ajaran & Semester.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val d = DialogAddClassBinding.inflate(layoutInflater)
        val isEdit = existing != null
        var selectedYearIdx = years.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
        var selectedTeacherIdx = 0
        var selectedGrade = existing?.gradeLevel ?: "10"

        if (existing != null) {
            d.layoutAcademicYear.isVisible = false
            d.layoutHomeroom.isVisible = false
            d.etClassName.setText(existing.name)
            d.etCapacity.setText(existing.capacity.toString())
        } else {
            val yearOptions = years.map {
                AgnesianOption(
                    id = it.id.toString(),
                    title = if (it.isActive) "${it.name} (Aktif)" else it.name,
                    subtitle = "${it.startDate} s.d. ${it.endDate}"
                )
            }
            d.etAcademicYearSelector.setText(yearOptions[selectedYearIdx].title)
            bindSelectorField(
                layout = d.layoutAcademicYear,
                input = d.etAcademicYearSelector,
                sheetTitle = "Pilih Tahun Ajaran",
                options = { yearOptions },
                selectedIndex = { selectedYearIdx },
                onSelected = { idx, _ -> selectedYearIdx = idx }
            )

            val teachers = state.teachers
            fun buildTeacherOptions(): List<AgnesianOption> {
                val chosenYearId = years.getOrNull(selectedYearIdx)?.id
                val assignedByTeacher = state.classes
                    .filter { it.academicYearId == chosenYearId && it.homeroomTeacherId != null }
                    .associate { it.homeroomTeacherId!! to it.name }
                return buildList {
                    add(AgnesianOption("", "Belum diatur", "Wali kelas bisa ditetapkan nanti"))
                    addAll(teachers.map { teacher ->
                        val occupiedClass = assignedByTeacher[teacher.id]
                        val sub = if (occupiedClass != null) {
                            "NIP: ${teacher.employeeNumber} · Sudah wali kelas $occupiedClass"
                        } else {
                            "NIP: ${teacher.employeeNumber}"
                        }
                        AgnesianOption(teacher.id.toString(), teacher.name, sub)
                    })
                }
            }
            d.etHomeroomSelector.setText("Belum diatur")
            bindSelectorField(
                layout = d.layoutHomeroom,
                input = d.etHomeroomSelector,
                sheetTitle = "Pilih Wali Kelas (Opsional)",
                options = ::buildTeacherOptions,
                selectedIndex = { selectedTeacherIdx },
                onSelected = { idx, _ -> selectedTeacherIdx = idx }
            )
            d.etCapacity.setText("36")
        }

        val gradeButtons = listOf(
            "10" to d.btnGrade10,
            "11" to d.btnGrade11,
            "12" to d.btnGrade12
        )
        fun renderGrades() {
            gradeButtons.forEach { (grade, btn) ->
                val active = grade == selectedGrade
                btn.setBackgroundResource(if (active) R.drawable.bg_option_selected else R.drawable.bg_option_unselected)
                btn.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (active) R.color.agnesian_blue else R.color.agnesian_text
                    )
                )
            }
        }
        gradeButtons.forEach { (grade, btn) ->
            btn.setOnClickListener {
                selectedGrade = grade
                renderGrades()
            }
        }
        renderGrades()

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(if (isEdit) R.string.dialog_edit_class_title else R.string.dialog_add_class_title),
            subtitle = if (isEdit) "Ubah nama, tingkat, atau kapasitas rombel" else "Tambahkan rombongan belajar baru",
            contentView = d.root,
            primaryLabel = if (isEdit) "Simpan" else "Tambah"
        ) {
            val name = d.etClassName.text?.toString()?.trim().orEmpty()
            val cap = d.etCapacity.text?.toString()?.toIntOrNull()
            if (name.isBlank() || cap == null || cap <= 0) {
                Toast.makeText(requireContext(), "Nama kelas dan kapasitas valid wajib diisi", Toast.LENGTH_SHORT).show()
                return@showAgnesianFormSheet false
            }
            if (existing == null) {
                val year = years.getOrNull(selectedYearIdx) ?: return@showAgnesianFormSheet false
                val teacherId = if (selectedTeacherIdx <= 0) null else state.teachers.getOrNull(selectedTeacherIdx - 1)?.id
                viewModel.createClass(year.id, name, selectedGrade, cap, teacherId)
            } else {
                viewModel.updateClass(existing.id, name, selectedGrade, cap)
            }
            true
        }
    }

    private fun showHomeroomDialog(item: MasterClassGroup) {
        val state = viewModel.uiState.value
        val teachers = state.teachers
        val occupiedByTeacher = state.classes
            .filter { it.academicYearId == item.academicYearId && it.id != item.id && it.homeroomTeacherId != null }
            .associate { it.homeroomTeacherId!! to it.name }
        val d = DialogHomeroomTeacherBinding.inflate(layoutInflater)
        val teacherOptions = buildList {
            add(AgnesianOption("", "Lepas Wali Kelas", "Kosongkan penugasan wali untuk kelas ini"))
            addAll(teachers.map { teacher ->
                val occupiedClass = occupiedByTeacher[teacher.id]
                val sub = if (occupiedClass != null) {
                    "NIP: ${teacher.employeeNumber} · Sudah wali kelas $occupiedClass"
                } else {
                    "NIP: ${teacher.employeeNumber}"
                }
                AgnesianOption(teacher.id.toString(), teacher.name, sub)
            })
        }

        var selectedIdx = if (item.homeroomTeacherId == null) 0 else {
            val found = teachers.indexOfFirst { it.id == item.homeroomTeacherId }
            if (found >= 0) found + 1 else 0
        }
        d.etTeacherSelector.setText(teacherOptions[selectedIdx].title)
        bindSelectorField(
            layout = d.layoutTeacher,
            input = d.etTeacherSelector,
            sheetTitle = "Pilih Guru Wali Kelas",
            options = { teacherOptions },
            selectedIndex = { selectedIdx },
            onSelected = { idx, _ -> selectedIdx = idx }
        )

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.dialog_homeroom_title),
            subtitle = "Kelas ${item.name} (${item.academicYearName})",
            contentView = d.root,
            primaryLabel = "Simpan"
        ) {
            val teacherId = if (selectedIdx == 0) null else teachers.getOrNull(selectedIdx - 1)?.id
            viewModel.setHomeroomTeacher(item.id, teacherId)
            true
        }
    }

    private fun showMembersDialog(item: MasterClassGroup) {
        val d = DialogClassMembersBinding.inflate(layoutInflater)
        d.tvClassSummary.text = "Kelas ${item.name} · Kapasitas ${item.capacity}"

        var selectedStudentIdx = -1
        var renderedStudents: List<MasterStudent> = emptyList()

        val sheet = showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.dialog_class_members_title),
            subtitle = "Kelas ${item.name} (${item.academicYearName})",
            contentView = d.root,
            secondaryLabel = "Tutup",
            showPrimary = false,
            onPrimary = { true }
        )

        bindSelectorField(
            layout = d.layoutStudent,
            input = d.etStudentSelector,
            sheetTitle = "Pilih Siswa untuk Kelas ${item.name}",
            options = {
                renderedStudents.map { st ->
                    val cls = st.className ?: "Belum ada kelas"
                    AgnesianOption(st.id.toString(), st.name, "NIS: ${st.nis} · $cls")
                }
            },
            selectedIndex = { selectedStudentIdx },
            onSelected = { idx, _ -> selectedStudentIdx = idx }
        )

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
                renderedStudents = state.studentsLookup

                val page = state.selectedMembersPage
                if (page != null && page.classId == item.id) {
                    d.tvClassSummary.text = "Kelas ${page.className} · Terisi ${page.members.size} / ${page.capacity}"
                    d.tvMembersEmpty.isVisible = !state.isMembersLoading && page.members.isEmpty()

                    d.containerMembers.removeAllViews()
                    page.members.forEach { member ->
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
                            text = "${member.name}\nNIS: ${member.nis}"
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.agnesian_text))
                            textSize = 13f
                        }
                        val btnMove = MaterialButton(
                            requireContext(),
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle
                        ).apply {
                            text = "Pindah"
                            isAllCaps = false
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.agnesian_blue))
                            strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.agnesian_blue)
                            setOnClickListener { showMoveMemberPicker(item, member.studentId, member.name) }
                        }
                        val btnRemove = MaterialButton(
                            requireContext(),
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle
                        ).apply {
                            text = "Hapus"
                            isAllCaps = false
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.change_cancel))
                            strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.change_cancel)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { marginStart = 12 }
                            setOnClickListener {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Keluarkan Siswa?")
                                    .setMessage("Keluarkan ${member.name} dari kelas ${item.name}?")
                                    .setNegativeButton("Batal", null)
                                    .setPositiveButton("Keluarkan") { _, _ ->
                                        viewModel.removeClassMember(item.id, member.studentId)
                                    }
                                    .show()
                            }
                        }
                        row.addView(info)
                        row.addView(btnMove)
                        row.addView(btnRemove)
                        d.containerMembers.addView(row)
                    }
                }
            }
        }

        sheet.setOnDismissListener { job.cancel() }
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
        showAgnesianOptionSheet(
            context = requireContext(),
            title = "Pindahkan $studentName",
            subtitle = "Pilih kelas tujuan di tahun ajaran ${sourceClass.academicYearName}",
            options = targets.map {
                AgnesianOption(it.id.toString(), it.name, "Terisi ${it.memberCount}/${it.capacity} siswa")
            },
            selectedIndex = -1,
            onSelected = { idx, _ ->
                viewModel.moveClassMember(sourceClass.id, studentId, targets[idx].id)
            }
        )
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
