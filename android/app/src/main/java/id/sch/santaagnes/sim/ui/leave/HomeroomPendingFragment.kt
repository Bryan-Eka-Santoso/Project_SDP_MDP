package id.sch.santaagnes.sim.ui.leave

import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import id.sch.santaagnes.sim.data.repository.HomeroomLeaveRepository
import java.io.File
import javax.inject.Inject
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.HomeroomLeaveDto
import id.sch.santaagnes.sim.databinding.FragmentHomeroomPendingBinding
import id.sch.santaagnes.sim.databinding.ItemHomeroomPendingBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class HomeroomPendingFragment : Fragment() {
    private var _binding: FragmentHomeroomPendingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeroomPendingViewModel by viewModels()
    @Inject lateinit var leaveRepository: HomeroomLeaveRepository
    private val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentHomeroomPendingBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnLeaveBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnLeaveReload.setOnClickListener { viewModel.refresh() }
        binding.btnLeaveMore.setOnClickListener { viewModel.loadMore() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: HomeroomPendingState) {
        binding.progressPending.isVisible = state.loading || state.loadingMore || state.decisionBusyId != null
        binding.btnLeaveMore.isVisible = state.hasMore && !state.loading && !state.loadingMore && state.decisionBusyId == null
        binding.btnLeaveReload.isVisible = state.decisionBusyId == null && (state.error != null || (!state.loading && state.items.isEmpty()))
        binding.tvLeaveStatus.text = when {
            state.error != null -> state.error
            state.notice != null -> state.notice
            state.loading -> "Memuat pengajuan izin..."
            state.items.isEmpty() -> "Tidak ada pengajuan izin yang menunggu persetujuan."
            else -> "${state.total} pengajuan menunggu persetujuan"
        }
        binding.listPending.removeAllViews()
        state.items.forEach { item ->
            val card = ItemHomeroomPendingBinding.inflate(layoutInflater, binding.listPending, false)
            card.tvPendingName.text = item.studentName
            card.tvPendingNis.text = item.nis?.let { "NIS $it" } ?: "Siswa"
            card.tvPendingDate.text = "${formatDate(item.startDate)} – ${formatDate(item.endDate)}"
            card.tvPendingReason.text = item.reason
            val busy = state.decisionBusyId != null
            card.btnPendingApprove.isEnabled = !busy
            card.btnPendingReject.isEnabled = !busy
            card.btnPendingProof.isEnabled = !busy
            card.btnPendingApprove.setOnClickListener { confirmDecision(item, approved = true) }
            card.btnPendingReject.setOnClickListener { confirmDecision(item, approved = false) }
            val hasProof = !item.attachmentUrl.isNullOrBlank()
            card.btnPendingProof.isVisible = hasProof
            card.btnPendingProof.setOnClickListener {
                if (hasProof) openProof(item, card.btnPendingProof)
            }
            binding.listPending.addView(card.root)
        }
    }

    private fun openProof(item: HomeroomLeaveDto, button: View) {
        val context = requireContext()
        val ext = item.attachmentUrl?.substringAfterLast('.')?.lowercase()
        if (ext !in listOf("jpg", "png", "pdf")) {
            Toast.makeText(context, "Format lampiran tidak didukung.", Toast.LENGTH_LONG).show()
            return
        }
        button.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val target = File(File(context.cacheDir, "leave_proofs"), "izin_${item.id}.$ext")
                leaveRepository.downloadProof(item.id, target)
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", target
                )
                val mime = when (ext) {
                    "pdf" -> "application/pdf"
                    "png" -> "image/png"
                    else -> "image/jpeg"
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Buka lampiran izin"))
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Toast.makeText(
                    context,
                    "Gagal membuka lampiran: ${HomeroomLeaveRepository.friendlyError(error)}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                button.isEnabled = true
            }
        }
    }

    private fun confirmDecision(item: HomeroomLeaveDto, approved: Boolean) {
        val verb = if (approved) "Setujui" else "Tolak"
        val detail = if (approved) {
            "Izin akan disetujui dan dapat memperbarui data presensi sesuai aturan backend."
        } else {
            "Izin akan ditolak. Keputusan ini akan tersimpan di sistem."
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$verb izin ${item.studentName}?")
            .setMessage("${formatDate(item.startDate)} – ${formatDate(item.endDate)}\n\n$detail")
            .setNegativeButton("Batal", null)
            .setPositiveButton(verb) { _, _ -> viewModel.decide(item, approved) }
            .show()
    }

    private fun formatDate(raw: String): String = try {
        LocalDate.parse(raw.take(10)).format(formatter)
    } catch (_: Exception) { raw }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
