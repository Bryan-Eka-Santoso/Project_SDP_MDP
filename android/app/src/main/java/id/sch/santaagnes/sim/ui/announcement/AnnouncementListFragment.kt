package id.sch.santaagnes.sim.ui.announcement

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.DialogAnnouncementFormBinding
import id.sch.santaagnes.sim.databinding.FragmentAnnouncementsBinding
import id.sch.santaagnes.sim.domain.model.Announcement
import id.sch.santaagnes.sim.ui.admin.master.AgnesianOption
import id.sch.santaagnes.sim.ui.admin.master.bindDateField
import id.sch.santaagnes.sim.ui.admin.master.bindSelectorField
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AnnouncementListFragment : Fragment() {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AnnouncementViewModel by viewModels()

    private val adapter by lazy {
        AnnouncementAdapter(
            canManage = { viewModel.uiState.value.canManage(it) },
            onOpenAttachment = ::openAttachmentUrl,
            onEdit = { showFormSheet(it) },
            onDelete = ::confirmDelete
        )
    }

    private var searchJob: Job? = null
    private var pendingAttachmentCallback: ((String) -> Unit)? = null

    private val pickAttachment =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { pendingAttachmentCallback?.invoke(it.toString()) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnnouncementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAnnouncements.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAdd.setOnClickListener { showFormSheet(null) }

        setupFilterAndSearch()
        observeViewModel()
    }

    private fun setupFilterAndSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                viewModel.setSearchQuery(editable?.toString().orEmpty())
            }
        }

        binding.btnScopeAll.setOnClickListener { viewModel.setScopeFilter(null) }
        binding.btnScopeSchool.setOnClickListener { viewModel.setScopeFilter("SCHOOL") }
        binding.btnScopeClass.setOnClickListener { viewModel.setScopeFilter("CLASS") }

        bindSelectorField(
            layout = binding.layoutClassFilter,
            input = binding.etClassFilter,
            sheetTitle = "Filter Pengumuman Kelas",
            options = {
                listOf(AgnesianOption(id = "", title = "Semua Kelas")) +
                    viewModel.uiState.value.filterClasses.map { cls ->
                        AgnesianOption(id = cls.id.toString(), title = cls.name)
                    }
            },
            selectedIndex = {
                val activeId = viewModel.uiState.value.selectedClassFilterId
                if (activeId == null) {
                    0
                } else {
                    val idx = viewModel.uiState.value.filterClasses.indexOfFirst { it.id == activeId }
                    if (idx >= 0) idx + 1 else 0
                }
            }
        ) { _, opt ->
            viewModel.setClassFilter(opt.id.toIntOrNull())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.btnAdd.isVisible = state.canCreate
                        binding.tvOfflineBanner.isVisible = state.isFromCache
                        binding.progressBar.isVisible = state.isLoading || state.isSubmitting
                        binding.tvEmptyState.isVisible = !state.isLoading && state.items.isEmpty()

                        renderScopeFilter(state.selectedScopeFilter)
                        renderClassFilter(state)
                        adapter.submitList(state.items)
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

    private fun renderScopeFilter(activeScope: String?) {
        val pills = listOf(
            binding.btnScopeAll to null,
            binding.btnScopeSchool to "SCHOOL",
            binding.btnScopeClass to "CLASS"
        )
        pills.forEach { (view, scope) ->
            val selected = activeScope == scope
            view.setBackgroundResource(
                if (selected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
            )
            view.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.agnesian_blue else R.color.agnesian_text
                )
            )
        }
    }

    private fun renderClassFilter(state: AnnouncementUiState) {
        binding.layoutClassFilter.isVisible = state.canFilterByClass
        if (!state.canFilterByClass) return

        val selectedLabel = state.selectedClassFilterId?.let { id ->
            state.filterClasses.firstOrNull { it.id == id }?.name
        } ?: "Semua Kelas"
        if (binding.etClassFilter.text?.toString() != selectedLabel) {
            binding.etClassFilter.setText(selectedLabel)
        }
    }

    private fun showFormSheet(existing: Announcement?) {
        val state = viewModel.uiState.value
        val d = DialogAnnouncementFormBinding.inflate(layoutInflater)
        val isAdmin = state.isAdmin
        val targetClasses = state.formTargetClasses()
        var selectedScope = existing?.scope ?: if (isAdmin) "SCHOOL" else "CLASS"
        var selectedClassId = existing?.classGroupId ?: targetClasses.firstOrNull()?.id

        fun syncTargetUi() {
            val scopePills = listOf(
                d.btnTargetSchool to "SCHOOL",
                d.btnTargetClass to "CLASS"
            )
            scopePills.forEach { (view, pillScope) ->
                val selected = selectedScope == pillScope
                view.setBackgroundResource(
                    if (selected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
                )
                view.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (selected) R.color.agnesian_blue else R.color.agnesian_text
                    )
                )
            }

            if (selectedScope == "SCHOOL") {
                d.tvTargetInfo.text = "Pengumuman Tingkat Sekolah (terkirim ke semua pengguna)"
                d.layoutClassGroup.isVisible = false
            } else {
                val classLabel = existing?.className
                    ?: targetClasses.firstOrNull { it.id == selectedClassId }?.name
                d.tvTargetInfo.text = if (isAdmin) {
                    "Pengumuman Khusus Kelas (${classLabel ?: "belum dipilih"})"
                } else {
                    "Pengumuman Kelas Binaan (${classLabel ?: "-"})"
                }
                d.layoutClassGroup.isVisible = existing == null && targetClasses.isNotEmpty()
                d.etClassGroupSelector.setText(
                    classLabel ?: if (targetClasses.isEmpty()) "Belum ada kelas aktif" else ""
                )
            }
        }

        d.layoutScopeSelector.isVisible = existing == null && isAdmin
        if (existing == null && isAdmin) {
            d.btnTargetSchool.setOnClickListener {
                selectedScope = "SCHOOL"
                syncTargetUi()
            }
            d.btnTargetClass.setOnClickListener {
                selectedScope = "CLASS"
                if (selectedClassId == null) {
                    selectedClassId = targetClasses.firstOrNull()?.id
                }
                syncTargetUi()
            }
        }

        if (existing == null && targetClasses.isNotEmpty()) {
            bindSelectorField(
                layout = d.layoutClassGroup,
                input = d.etClassGroupSelector,
                sheetTitle = if (isAdmin) "Pilih Kelas Tujuan" else "Pilih Kelas Binaan",
                options = {
                    targetClasses.map { cls ->
                        AgnesianOption(id = cls.id.toString(), title = cls.name)
                    }
                },
                selectedIndex = { targetClasses.indexOfFirst { it.id == selectedClassId } }
            ) { idx, _ ->
                targetClasses.getOrNull(idx)?.let { cls ->
                    selectedClassId = cls.id
                    syncTargetUi()
                }
            }
        }
        syncTargetUi()

        d.etTitle.setText(existing?.title.orEmpty())
        d.etContent.setText(existing?.content.orEmpty())
        d.etDate.setText(existing?.date ?: DateTimeWib.today().toString())
        bindDateField(d.layoutDate, d.etDate, childFragmentManager, "Tanggal Pengumuman")

        var pickedLocalUri: String? = null
        var existingUrl = existing?.attachmentUrl
        val existingPublicId = existing?.attachmentPublicId
        var clearAttachment = false

        fun renderAttachmentRow(displayName: String?) {
            val hasFile = !displayName.isNullOrBlank()
            d.tvAttachmentName.text = displayName ?: getString(R.string.announcement_no_attachment)
            d.tvAttachmentName.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (hasFile) R.color.agnesian_text else R.color.agnesian_muted
                )
            )
            d.btnClearAttachment.isVisible = hasFile
        }

        renderAttachmentRow(existingUrl?.let { "Lampiran terpasang" })

        pendingAttachmentCallback = { uriString ->
            pickedLocalUri = uriString
            clearAttachment = false
            viewLifecycleOwner.lifecycleScope.launch {
                val resolved = viewModel.resolveAttachmentName(uriString) ?: "Berkas dipilih"
                if (_binding != null) {
                    renderAttachmentRow(resolved)
                }
            }
        }

        d.btnPickAttachment.setOnClickListener {
            pickAttachment.launch(arrayOf("image/jpeg", "image/png", "application/pdf"))
        }
        d.btnClearAttachment.setOnClickListener {
            pickedLocalUri = null
            existingUrl = null
            clearAttachment = existing?.attachmentUrl != null
            renderAttachmentRow(null)
        }

        val sheet = showAgnesianFormSheet(
            context = requireContext(),
            title = getString(
                if (existing == null) R.string.announcement_add_title else R.string.announcement_edit_title
            ),
            subtitle = if (existing == null) "Pengguna akan menerima notifikasi otomatis" else "Perbarui isi atau lampiran pengumuman",
            contentView = d.root,
            primaryLabel = if (existing == null) "Kirim" else "Simpan"
        ) {
            val title = d.etTitle.text?.toString()?.trim().orEmpty()
            val content = d.etContent.text?.toString()?.trim().orEmpty()
            val date = d.etDate.text?.toString()?.trim().orEmpty()

            if (title.isEmpty() || content.isEmpty() || date.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Judul, isi, dan tanggal pengumuman wajib diisi",
                    Toast.LENGTH_SHORT
                ).show()
                return@showAgnesianFormSheet false
            }
            if (selectedScope == "CLASS" && selectedClassId == null) {
                Toast.makeText(requireContext(), "Pilih kelas tujuan pengumuman", Toast.LENGTH_SHORT).show()
                return@showAgnesianFormSheet false
            }

            viewModel.saveAnnouncement(
                editingId = existing?.id,
                title = title,
                content = content,
                scope = selectedScope,
                classGroupId = selectedClassId,
                date = date,
                pickedLocalUri = pickedLocalUri,
                existingUrl = existing?.attachmentUrl,
                existingPublicId = existingPublicId,
                clearAttachment = clearAttachment
            )
            true
        }
        sheet.setOnDismissListener { pendingAttachmentCallback = null }
    }

    private fun openAttachmentUrl(item: Announcement) {
        val url = item.attachmentUrl ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(requireContext(), "Tidak ada aplikasi untuk membuka lampiran", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(item: Announcement) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Pengumuman?")
            .setMessage("Pengumuman \"${item.title}\" akan dihapus permanen.")
            .setPositiveButton("Hapus") { _, _ -> viewModel.deleteAnnouncement(item.id) }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingAttachmentCallback = null
        _binding = null
    }
}
