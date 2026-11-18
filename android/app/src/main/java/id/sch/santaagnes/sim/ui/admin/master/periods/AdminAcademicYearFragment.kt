package id.sch.santaagnes.sim.ui.admin.master.periods

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import id.sch.santaagnes.sim.ui.admin.master.setupMasterHeader
import id.sch.santaagnes.sim.ui.admin.master.showIsoDatePicker
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
            onEdit = ::showEditYearDialog,
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
            onAdd = ::showAddYearDialog
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

    private fun showAddYearDialog() {
        val d = DialogAddAcademicYearBinding.inflate(layoutInflater)
        d.etStartDate.setOnClickListener {
            showIsoDatePicker(requireContext(), d.etStartDate.text?.toString()) { d.etStartDate.setText(it) }
        }
        d.etEndDate.setOnClickListener {
            showIsoDatePicker(requireContext(), d.etEndDate.text?.toString()) { d.etEndDate.setText(it) }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Tambah") { _, _ ->
                val name = d.etName.text.toString().trim()
                val start = d.etStartDate.text.toString().trim()
                val end = d.etEndDate.text.toString().trim()
                if (name.isBlank() || start.isBlank() || end.isBlank()) {
                    Toast.makeText(requireContext(), "Nama dan tanggal mulai/selesai wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createYear(name, start, end, d.switchActive.isChecked)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditYearDialog(year: MasterAcademicYear) {
        val d = DialogAddAcademicYearBinding.inflate(layoutInflater)
        d.tvDialogTitle.setText(R.string.dialog_edit_period_title)
        d.etName.setText(year.name)
        d.etStartDate.setText(year.startDate)
        d.etEndDate.setText(year.endDate)
        d.switchActive.isVisible = false

        d.etStartDate.setOnClickListener {
            showIsoDatePicker(requireContext(), d.etStartDate.text?.toString()) { d.etStartDate.setText(it) }
        }
        d.etEndDate.setOnClickListener {
            showIsoDatePicker(requireContext(), d.etEndDate.text?.toString()) { d.etEndDate.setText(it) }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setPositiveButton("Simpan") { _, _ ->
                val name = d.etName.text.toString().trim()
                val start = d.etStartDate.text.toString().trim()
                val end = d.etEndDate.text.toString().trim()
                if (name.isBlank() || start.isBlank() || end.isBlank()) {
                    Toast.makeText(requireContext(), "Semua kolom wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateYear(year.id, name, start, end)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showSemestersDialog(year: MasterAcademicYear) {
        val d = DialogManageSemestersBinding.inflate(layoutInflater)
        d.tvYearHeader.text = "Tahun Ajaran ${year.name} (${year.startDate} s.d. ${year.endDate})"

        val types = arrayOf("ODD (Ganjil)", "EVEN (Genap)")
        var selectedType = "ODD"
        d.spinnerSemesterType.setSimpleItems(types)
        d.spinnerSemesterType.setText(types[0], false)
        d.spinnerSemesterType.setOnItemClickListener { _, _, pos, _ ->
            selectedType = if (pos == 0) "ODD" else "EVEN"
        }

        d.etSemStart.setOnClickListener {
            showIsoDatePicker(requireContext(), d.etSemStart.text?.toString()) { d.etSemStart.setText(it) }
        }
        d.etSemEnd.setOnClickListener {
            showIsoDatePicker(requireContext(), d.etSemEnd.text?.toString()) { d.etSemEnd.setText(it) }
        }

        d.btnAddSemester.setOnClickListener {
            val start = d.etSemStart.text.toString().trim()
            val end = d.etSemEnd.text.toString().trim()
            if (start.isBlank() || end.isBlank()) {
                Toast.makeText(requireContext(), "Tanggal mulai & selesai semester wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.createSemester(year.id, selectedType, start, end, d.switchSemActive.isChecked)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(d.root)
            .setNegativeButton("Tutup", null)
            .create()

        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                d.progressDialog.isVisible = state.isSemestersLoading
                d.tvSemestersEmpty.isVisible = !state.isSemestersLoading && state.semestersForYear.isEmpty()

                d.containerSemesters.removeAllViews()
                state.semestersForYear.forEach { sem ->
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 8, 0, 8)
                    }
                    val statusTag = if (sem.isActive) " [AKTIF]" else ""
                    val info = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        text = "${sem.label}$statusTag\n${sem.startDate} s.d. ${sem.endDate}"
                        textSize = 13f
                    }
                    if (!sem.isActive) {
                        val btnActivate = MaterialButton(
                            requireContext(),
                            null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle
                        ).apply {
                            text = "Aktifkan"
                            isAllCaps = false
                            setOnClickListener { viewModel.setActiveSemester(year.id, sem.id) }
                        }
                        row.addView(info)
                        row.addView(btnActivate)
                    } else {
                        row.addView(info)
                    }
                    val btnDel = MaterialButton(
                        requireContext(),
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = "Hapus"
                        isAllCaps = false
                        setOnClickListener { viewModel.deleteSemester(year.id, sem.id) }
                    }
                    row.addView(btnDel)
                    d.containerSemesters.addView(row)
                }
            }
        }

        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
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
