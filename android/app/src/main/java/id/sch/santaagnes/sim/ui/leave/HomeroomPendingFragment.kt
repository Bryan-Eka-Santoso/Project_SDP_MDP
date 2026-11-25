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
        if (approved) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Setujui izin ${item.studentName}?")
                .setMessage(
                    "${formatDate(item.startDate)} – ${formatDate(item.endDate)}\n\n" +
                        "Izin akan disetujui dan presensi siswa pada tanggal tersebut akan diperbarui otomatis."
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton("Setujui") { _, _ -> viewModel.decide(item, approved = true) }
                .show()
            return
        }

        val density = resources.displayMetrics.density
        val pad = (density * 20).toInt()
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, (density * 8).toInt(), pad, 0)
        }
        val subtitle = TextView(requireContext()).apply {
            text = "${formatDate(item.startDate)} – ${formatDate(item.endDate)}\nTuliskan alasan penolakan agar wali murid mengetahuinya."
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.agnesian_text))
        }
        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            requireContext(),
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = "Alasan penolakan (wajib diisi)"
            isCounterEnabled = true
            counterMaxLength = 255
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (density * 12).toInt()
            }
        }
        val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            minLines = 2
            maxLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(255))
        }
        inputLayout.addView(editText)
        container.addView(subtitle)
        container.addView(inputLayout)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tolak izin ${item.studentName}?")
            .setView(container)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Tolak", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason = editText.text?.toString()?.trim().orEmpty()
                if (reason.isBlank()) {
                    inputLayout.error = "Alasan penolakan wajib diisi."
                    return@setOnClickListener
                }
                inputLayout.error = null
                dialog.dismiss()
                viewModel.decide(item, approved = false, rejectionReason = reason)
            }
        }
        dialog.show()
    }

    private fun formatDate(raw: String): String = try {
        LocalDate.parse(raw.take(10)).format(formatter)
    } catch (_: Exception) { raw }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
