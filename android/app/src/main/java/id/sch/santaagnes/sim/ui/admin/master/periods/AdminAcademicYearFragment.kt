package id.sch.santaagnes.sim.ui.admin.master.periods

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
import id.sch.santaagnes.sim.databinding.DialogAddAcademicYearBinding
import id.sch.santaagnes.sim.databinding.DialogManageSemestersBinding
import id.sch.santaagnes.sim.databinding.FragmentAdminAcademicYearsBinding
import id.sch.santaagnes.sim.domain.model.MasterAcademicYear
import id.sch.santaagnes.sim.ui.admin.master.bindDateField
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminAcademicYearFragment : Fragment() {

    private var _binding: FragmentAdminAcademicYearsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminAcademicYearViewModel by viewModels()

    private val adapter by lazy {
        AdminAcademicYearAdapter(
            onSetActive = { year -> viewModel.setActiveYear(year.id) },
            onManageSemesters = ::showSemestersDialog,
            onEdit = { showYearFormSheet(it) },
            onDelete = ::confirmDeleteYear
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAcademicYearsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nav = findNavController()
        binding.rvYears.layoutManager = LinearLayoutManager(requireContext())
        binding.rvYears.adapter = adapter

        setupMasterHeader(
            header = binding.header,
            navController = nav,
            titleRes = R.string.title_management_period,
            showImport = false,
            onAdd = { showYearFormSheet(null) }
        )
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.header.tvSemesterBadge.text =
                            state.activePeriodText ?: getString(R.string.master_period_loading)
                        binding.progressBar.isVisible = state.isLoading
                        binding.tvEmptyState.isVisible = !state.isLoading && state.years.isEmpty()
                        adapter.submitList(state.years)

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

    private fun showYearFormSheet(existing: MasterAcademicYear?) {
        val d = DialogAddAcademicYearBinding.inflate(layoutInflater)
        val isEdit = existing != null
        if (existing != null) {
            d.etName.setText(existing.name)
            d.etStartDate.setText(existing.startDate)
            d.etEndDate.setText(existing.endDate)
            d.switchActive.isVisible = false
        }

        bindDateField(d.layoutStartDate, d.etStartDate, childFragmentManager, "Pilih Tanggal Mulai")
        bindDateField(d.layoutEndDate, d.etEndDate, childFragmentManager, "Pilih Tanggal Selesai")

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(if (isEdit) R.string.dialog_edit_period_title else R.string.dialog_add_period_title),
            subtitle = if (isEdit) "Ubah rentang tanggal atau nama tahun ajaran" else "Buat periode tahun ajaran baru",
            contentView = d.root,
            primaryLabel = if (isEdit) "Simpan" else "Tambah"
        ) {
            val name = d.etName.text?.toString()?.trim().orEmpty()
            val start = d.etStartDate.text?.toString()?.trim().orEmpty()
            val end = d.etEndDate.text?.toString()?.trim().orEmpty()
            if (name.isBlank() || start.isBlank() || end.isBlank()) {
                Toast.makeText(requireContext(), "Nama dan tanggal mulai/selesai wajib diisi", Toast.LENGTH_SHORT).show()
                return@showAgnesianFormSheet false
            }
            if (existing == null) {
                viewModel.createYear(name, start, end, d.switchActive.isChecked)
            } else {
                viewModel.updateYear(existing.id, name, start, end)
            }
            true
        }
    }

    private fun showSemestersDialog(year: MasterAcademicYear) {
        val d = DialogManageSemestersBinding.inflate(layoutInflater)

        var selectedType = "ODD"
        fun renderSemesterType() {
            val isOdd = selectedType == "ODD"
            d.btnSemOdd.setBackgroundResource(if (isOdd) R.drawable.bg_option_selected else R.drawable.bg_option_unselected)
            d.btnSemOdd.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isOdd) R.color.agnesian_blue else R.color.agnesian_text
                )
            )
            d.btnSemEven.setBackgroundResource(if (!isOdd) R.drawable.bg_option_selected else R.drawable.bg_option_unselected)
            d.btnSemEven.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (!isOdd) R.color.agnesian_blue else R.color.agnesian_text
                )
            )
        }
        d.btnSemOdd.setOnClickListener {
            selectedType = "ODD"
            renderSemesterType()
        }
        d.btnSemEven.setOnClickListener {
            selectedType = "EVEN"
            renderSemesterType()
        }
        renderSemesterType()

        bindDateField(d.layoutSemStart, d.etSemStart, childFragmentManager, "Mulai Semester")
        bindDateField(d.layoutSemEnd, d.etSemEnd, childFragmentManager, "Selesai Semester")

        d.btnAddSemester.setOnClickListener {
            val start = d.etSemStart.text?.toString()?.trim().orEmpty()
            val end = d.etSemEnd.text?.toString()?.trim().orEmpty()
            if (start.isBlank() || end.isBlank()) {
                Toast.makeText(requireContext(), "Tanggal mulai & selesai semester wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.createSemester(year.id, selectedType, start, end, d.switchSemActive.isChecked)
        }

        val sheet = showAgnesianFormSheet(
            context = requireContext(),
            title = getString(R.string.dialog_manage_semesters_title),
            subtitle = "Tahun Ajaran ${year.name} (${year.startDate} s.d. ${year.endDate})",
            contentView = d.root,
            secondaryLabel = "Tutup",
            showPrimary = false,
            onPrimary = { true }
        )

        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                d.progressDialog.isVisible = state.isSemestersLoading
                d.tvSemestersEmpty.isVisible = !state.isSemestersLoading && state.semestersForYear.isEmpty()

                d.containerSemesters.removeAllViews()
                state.semestersForYear.forEach { sem ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setBackgroundResource(
                            if (sem.isActive) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
                        )
                        setPadding(24, 18, 24, 18)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12 }
                    }
                    val statusTag = if (sem.isActive) " · AKTIF" else ""
                    val info = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        text = "${sem.label}$statusTag\n${sem.startDate} s.d. ${sem.endDate}"
                        setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (sem.isActive) R.color.agnesian_blue else R.color.agnesian_text
                            )
                        )
                        textSize = 13f
                    }
                    row.addView(info)
                    if (!sem.isActive) {
                        val btnActivate = MaterialButton(
                            requireContext(),
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle
                        ).apply {
                            text = "Aktifkan"
                            isAllCaps = false
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.agnesian_blue))
                            strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.agnesian_blue)
                            setOnClickListener { viewModel.setActiveSemester(year.id, sem.id) }
                        }
                        row.addView(btnActivate)
                    }
                    val btnDel = MaterialButton(
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
                        ).apply { marginStart = 10 }
                        setOnClickListener { viewModel.deleteSemester(year.id, sem.id) }
                    }
                    row.addView(btnDel)
                    d.containerSemesters.addView(row)
                }
            }
        }

        sheet.setOnDismissListener { job.cancel() }
        viewModel.loadSemesters(year.id)
    }

    private fun confirmDeleteYear(year: MasterAcademicYear) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Tahun Ajaran?")
            .setMessage("Tahun ajaran ${year.name} akan dihapus permanen.\n\nCatatan: Tahun ajaran yang sudah memiliki kelas atau data akademik tidak dapat dihapus.")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteYear(year.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
