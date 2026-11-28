package id.sch.santaagnes.sim.ui.leave

import id.sch.santaagnes.sim.core.setLoading
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentLeaveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class LeaveFragment : Fragment() {
    private var _binding: FragmentLeaveBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LeaveViewModel by viewModels()
    private var selectedChildId: Int? = null
    private var childIds: List<Int> = emptyList()
    private var lastChildIds: List<Int> = emptyList()
    private var startDate: String = today()
    private var endDate: String = today()
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String? = null
    private var selectedMime: String? = null

    private val selectAttachment = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && _binding != null) {
            selectedFileUri = uri
            val context = requireContext()
            var name: String? = null
            var size: Long? = null
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
            val mime = context.contentResolver.getType(uri)
            val actualName = name ?: "lampiran"
            val allowed = when {
                actualName.endsWith(".pdf", true) -> "application/pdf"
                actualName.endsWith(".png", true) -> "image/png"
                actualName.endsWith(".jpg", true) || actualName.endsWith(".jpeg", true) -> "image/jpeg"
                else -> null
            }
            if (allowed == null || (mime != null && mime != "application/octet-stream" && mime != allowed)) {
                clearAttachment()
                binding.tvFeedback.setTextColor(0xFFB3261E.toInt())
                binding.tvFeedback.text = "Lampiran tidak valid. Pilih berkas JPG, PNG, atau PDF."
            } else if (size != null && size!! > MAX_FILE_BYTES) {
                clearAttachment()
                binding.tvFeedback.setTextColor(0xFFB3261E.toInt())
                binding.tvFeedback.text = "Ukuran lampiran melebihi batas maksimal 5 MB."
            } else {
                selectedFileName = actualName
                selectedMime = allowed
                val sizeLabel = size?.let { String.format(Locale.US, " (%.1f MB)", it / (1024.0 * 1024.0)) }.orEmpty()
                binding.tvAttachment.setTextColor(0xFF176A42.toInt())
                binding.tvAttachment.text = "✓ Lampiran valid: $actualName$sizeLabel"
                binding.btnRemoveAttachment.visibility = View.VISIBLE
                binding.tvFeedback.text = ""
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentLeaveBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        renderDates()
        binding.btnStart.setOnClickListener { pickDate(startDate) { startDate = it; renderDates() } }
        binding.btnEnd.setOnClickListener { pickDate(endDate) { endDate = it; renderDates() } }
        binding.btnAttach.setOnClickListener { selectAttachment.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }
        binding.btnRemoveAttachment.setOnClickListener { clearAttachment() }
        binding.btnSubmit.setOnClickListener {
            val childId = selectedChildId ?: run {
                binding.tvFeedback.text = "Pilih anak terlebih dahulu."
                return@setOnClickListener
            }
            val reason = binding.etReason.text.toString().trim()
            if (reason.length < 5) {
                binding.etReason.error = "Alasan minimal 5 karakter."
                // Fokus membawa kolom yang salah ke layar dan memunculkan pesannya.
                binding.etReason.requestFocus()
                return@setOnClickListener
            }
            if (startDate > endDate) {
                binding.tvFeedback.text = "Tanggal selesai tidak boleh sebelum tanggal mulai."
                return@setOnClickListener
            }
            val uri = selectedFileUri
            val name = selectedFileName
            val mime = selectedMime
            if (uri == null || name == null || mime == null) {
                viewModel.create(childId, startDate, endDate, reason)
            } else {
                // Read on IO dispatcher, capped at 5 MB even when provider does not expose file size.
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.btnSubmit.isEnabled = false
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > MAX_FILE_BYTES) error("Lampiran maksimal 5 MB.")
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            } ?: error("Tidak bisa membuka lampiran.")
                        }
                        viewModel.create(childId, startDate, endDate, reason, bytes, name, mime)
                    } catch (e: Exception) {
                        binding.tvFeedback.text = e.message ?: "Gagal membaca lampiran."
                        binding.btnSubmit.isEnabled = true
                    }
                }
            }
        }
        binding.btnReload.setOnClickListener { selectedChildId?.let(viewModel::loadRequests) }
        binding.spinnerChild.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = childIds.getOrNull(position)
                if (next != null && next != selectedChildId) {
                    selectedChildId = next
                    viewModel.loadRequests(next)
                }
            }
        }
        viewModel.loadChildren()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.tvFeedback.setTextColor(
                        if (state.error != null) 0xFFB3261E.toInt() else 0xFF176A42.toInt()
                    )
                    binding.tvFeedback.text = state.error ?: state.message ?: if (!state.loading && state.children.isEmpty()) "Belum ada anak yang terhubung." else ""
                    if (!state.saving && state.message != null) {
                        binding.etReason.text?.clear()
                        clearAttachment()
                    }
                    val ids = state.children.map { it.id }
                    if (ids != lastChildIds) {
                        lastChildIds = ids
                        childIds = ids
                        binding.spinnerChild.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                            state.children.map { "${it.name} (${it.nis ?: "-"})" })
                        if (ids.isNotEmpty()) selectedChildId = ids.first()
                    }
                    // Dihitung setelah anak terpilih diisi; kalau tidak, tombol tetap nonaktif saat layar dibuka.
                    binding.btnSubmit.isEnabled = !state.loading && selectedChildId != null
                    binding.btnSubmit.setLoading(state.saving)
                    binding.listRequests.removeAllViews()
                    state.requests.forEach { request ->
                        val item = layoutInflater.inflate(R.layout.item_leave_request, binding.listRequests, false)
                        item.findViewById<TextView>(R.id.tvStudent).text = request.studentName
                        item.findViewById<TextView>(R.id.tvDates).text = "${request.startDate}  —  ${request.endDate}"
                        item.findViewById<TextView>(R.id.tvReason).text = request.reason
                        val status = item.findViewById<TextView>(R.id.tvStatus)
                        val style = when (request.status) {
                            "PENDING" -> Triple("Menunggu", R.drawable.bg_leave_status_pending, 0xFF996210.toInt())
                            "APPROVED" -> Triple("Disetujui", R.drawable.bg_leave_status_approved, 0xFF176A42.toInt())
                            "REJECTED" -> Triple("Ditolak", R.drawable.bg_leave_status_rejected, 0xFFAA3543.toInt())
                            else -> Triple(request.status, R.drawable.bg_leave_status_pending, 0xFF556278.toInt())
                        }
                        status.text = style.first
                        status.setBackgroundResource(style.second)
                        status.setTextColor(style.third)
                        item.findViewById<TextView>(R.id.tvHasAttachment).visibility =
                            if (request.attachmentUrl.isNullOrBlank()) View.GONE else View.VISIBLE
                        val rejectionBox = item.findViewById<View>(R.id.layoutRejectionReason)
                        val rejectionText = item.findViewById<TextView>(R.id.tvRejectionReason)
                        if (request.status == "REJECTED") {
                            rejectionBox.visibility = View.VISIBLE
                            rejectionText.text = request.rejectionReason?.takeIf { it.isNotBlank() }
                                ?: "Ditolak oleh wali kelas tanpa catatan tambahan."
                        } else {
                            rejectionBox.visibility = View.GONE
                        }
                        binding.listRequests.addView(item)
                    }
                    binding.tvEmpty.visibility = if (!state.loading && state.requests.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun renderDates() {
        binding.btnStart.text = "▦  $startDate  ·  Pilih tanggal"
        binding.btnEnd.text = "▦  $endDate  ·  Pilih tanggal"
    }

    private fun clearAttachment() {
        selectedFileUri = null
        selectedFileName = null
        selectedMime = null
        _binding?.let {
            it.tvAttachment.setTextColor(0xFF556278.toInt())
            it.tvAttachment.text = "Belum ada lampiran dipilih (JPG, PNG, PDF · Maks. 5 MB)"
            it.btnRemoveAttachment.visibility = View.GONE
        }
    }

    private fun pickDate(current: String, onChosen: (String) -> Unit) {
        val parts = current.split('-').map(String::toInt)
        DatePickerDialog(requireContext(), { _, y, m, d ->
            onChosen(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, parts[0], parts[1] - 1, parts[2]).show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    companion object {
        private const val MAX_FILE_BYTES = 5 * 1024 * 1024
        private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(Calendar.getInstance().time)
    }
}
