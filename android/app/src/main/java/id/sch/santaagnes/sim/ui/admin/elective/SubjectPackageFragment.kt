package id.sch.santaagnes.sim.ui.admin.elective

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
import id.sch.santaagnes.sim.databinding.DialogSubjectPackageBinding
import id.sch.santaagnes.sim.databinding.FragmentSubjectPackagesBinding
import id.sch.santaagnes.sim.databinding.ItemSheetOptionBinding
import id.sch.santaagnes.sim.domain.model.SubjectPackage
import id.sch.santaagnes.sim.ui.admin.master.AgnesianOption
import id.sch.santaagnes.sim.ui.admin.master.bindFilterPill
import id.sch.santaagnes.sim.ui.admin.master.bindSelectorField
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SubjectPackageFragment : Fragment() {

    private var _binding: FragmentSubjectPackagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubjectPackageViewModel by viewModels()

    private val adapter by lazy {
        SubjectPackageAdapter(
            onEdit = { showPackageFormSheet(it) },
            onDelete = ::confirmDeletePackage
        )
    }

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubjectPackagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPackages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPackages.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAdd.setOnClickListener { showPackageFormSheet(null) }

        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupFilterAndSearch() {
        binding.etSearchPackage.addTextChangedListener { editable ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                viewModel.setSearchQuery(editable?.toString().orEmpty())
            }
        }

        bindFilterPill(
            pill = binding.btnFilterYear,
            sheetTitle = "Pilih Tahun Ajaran",
            options = {
                viewModel.uiState.value.years.map { yr ->
                    AgnesianOption(
                        id = yr.id.toString(),
                        title = yr.name,
                        subtitle = if (yr.isActive) "Tahun Ajaran Aktif" else "${yr.startDate} s.d. ${yr.endDate}"
                    )
                }
            },
            selectedIndex = {
                val state = viewModel.uiState.value
                state.years.indexOfFirst { it.id == state.selectedYearId }
            }
        ) { idx, _ ->
            val year = viewModel.uiState.value.years.getOrNull(idx) ?: return@bindFilterPill
            viewModel.setAcademicYearFilter(year.id)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvPeriodBadge.text =
                            state.activePeriodText ?: getString(R.string.master_period_loading)
                        val selectedYear = state.years.firstOrNull { it.id == state.selectedYearId }
                        binding.btnFilterYear.text = selectedYear?.let {
                            if (it.isActive) "${it.name} (Aktif)" else it.name
                        } ?: "Tahun Ajaran"

                        binding.progressBar.isVisible = state.isLoading || state.isSubmitting
                        binding.tvEmptyState.isVisible = !state.isLoading && state.packages.isEmpty()
                        adapter.submitList(state.packages)
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

    private fun showPackageFormSheet(existing: SubjectPackage?) {
        val state = viewModel.uiState.value
        val years = state.years
        val subjects = state.subjects
        if (existing == null && years.isEmpty()) {
            Toast.makeText(requireContext(), "Data tahun ajaran belum siap", Toast.LENGTH_SHORT).show()
            return
        }
        if (subjects.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Belum ada data mata pelajaran. Tambahkan mata pelajaran terlebih dahulu.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val d = DialogSubjectPackageBinding.inflate(layoutInflater)
        var selectedYearId = existing?.academicYearId
            ?: state.selectedYearId
            ?: years.firstOrNull { it.isActive }?.id
            ?: years.first().id

        val selectedSubjectIds = linkedSetOf<Int>()
        if (existing != null) {
            selectedSubjectIds.addAll(existing.subjects.map { it.id })
            d.etPackageName.setText(existing.name)
            d.etCapacity.setText(existing.capacity.toString())
            d.layoutAcademicYear.isEnabled = false
            d.etAcademicYearSelector.setText(existing.academicYearName)
        } else {
            val defaultYear = years.firstOrNull { it.id == selectedYearId }
            d.etAcademicYearSelector.setText(defaultYear?.name.orEmpty())
            bindSelectorField(
                layout = d.layoutAcademicYear,
                input = d.etAcademicYearSelector,
                sheetTitle = "Pilih Tahun Ajaran",
                options = {
                    years.map { yr ->
                        AgnesianOption(
                            id = yr.id.toString(),
                            title = yr.name,
                            subtitle = if (yr.isActive) "Aktif" else "${yr.startDate} s.d. ${yr.endDate}"
                        )
                    }
                },
                selectedIndex = { years.indexOfFirst { it.id == selectedYearId } }
            ) { idx, _ ->
                years.getOrNull(idx)?.let { selectedYearId = it.id }
            }
        }

        fun renderChecklist(query: String = d.etSubjectSearch.text?.toString().orEmpty()) {
            d.tvSelectedSubjectsCount.text = "${selectedSubjectIds.size} dipilih"
            val q = query.trim().lowercase()
            val visible = if (q.isEmpty()) {
                subjects
            } else {
                subjects.filter {
                    it.name.lowercase().contains(q) ||
                        it.code.lowercase().contains(q) ||
                        it.type.lowercase().contains(q)
                }
            }
            d.tvSubjectsEmpty.isVisible = visible.isEmpty()
            d.containerSubjectChecklist.removeAllViews()

            visible.forEach { subject ->
                val row = ItemSheetOptionBinding.inflate(layoutInflater, d.containerSubjectChecklist, false)
                val checked = selectedSubjectIds.contains(subject.id)
                row.tvOptionTitle.text = "[${subject.code}] ${subject.name}"
                row.tvOptionTitle.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (checked) R.color.agnesian_blue else R.color.agnesian_text
                    )
                )
                row.tvOptionSubtitle.isVisible = true
                row.tvOptionSubtitle.text = subject.type
                row.ivSelected.isVisible = checked
                row.rowOption.setBackgroundResource(
                    if (checked) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
                )
                row.rowOption.setOnClickListener {
                    if (selectedSubjectIds.contains(subject.id)) {
                        selectedSubjectIds.remove(subject.id)
                    } else {
                        selectedSubjectIds.add(subject.id)
                    }
                    renderChecklist()
                }
                d.containerSubjectChecklist.addView(row.root)
            }
        }

        d.etSubjectSearch.addTextChangedListener { editable ->
            renderChecklist(editable?.toString().orEmpty())
        }
        renderChecklist("")

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(
                if (existing == null) R.string.dialog_add_package_title else R.string.dialog_edit_package_title
            ),
            subtitle = "Tentukan nama paket, kapasitas per kelas, dan anggota mata pelajarannya",
            contentView = d.root,
            primaryLabel = if (existing == null) "Simpan" else "Perbarui"
        ) {
            val name = d.etPackageName.text?.toString()?.trim().orEmpty()
            val capacity = d.etCapacity.text?.toString()?.trim()?.toIntOrNull()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Nama paket wajib diisi", Toast.LENGTH_SHORT).show()
                return@showAgnesianFormSheet false
            }
            if (capacity == null || capacity !in 1..100) {
                Toast.makeText(
                    requireContext(),
                    "Kapasitas per kelas harus antara 1 sampai 100",
                    Toast.LENGTH_SHORT
                ).show()
                return@showAgnesianFormSheet false
            }
            if (selectedSubjectIds.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Pilih minimal satu mata pelajaran",
                    Toast.LENGTH_SHORT
                ).show()
                return@showAgnesianFormSheet false
            }

            if (existing == null) {
                viewModel.createPackage(selectedYearId, name, capacity, selectedSubjectIds.toList())
            } else {
                viewModel.updatePackage(existing.id, name, capacity, selectedSubjectIds.toList())
            }
            true
        }
    }

    private fun confirmDeletePackage(item: SubjectPackage) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Paket Mapel?")
            .setMessage(
                "Paket ${item.name} akan dihapus permanen.\n\nCatatan: Paket yang sudah dipilih siswa tidak dapat dihapus."
            )
            .setPositiveButton("Hapus") { _, _ -> viewModel.deletePackage(item.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
