package id.sch.santaagnes.sim.ui.leave

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.databinding.FragmentLeaveBinding
import kotlinx.coroutines.launch
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
    private var startDate: String = today()
    private var endDate: String = today()
    private var lastChildIds: List<Int> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        _binding = FragmentLeaveBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnStart.text = "Mulai: $startDate"
        binding.btnEnd.text = "Selesai: $endDate"
        binding.btnStart.setOnClickListener { pickDate(startDate) {
            startDate = it
            binding.btnStart.text = "Mulai: $it"
        } }
        binding.btnEnd.setOnClickListener { pickDate(endDate) {
            endDate = it
            binding.btnEnd.text = "Selesai: $it"
        } }
        binding.btnSubmit.setOnClickListener {
            val childId = selectedChildId ?: run {
                binding.tvFeedback.text = "Pilih anak terlebih dahulu."
                return@setOnClickListener
            }
            val reason = binding.etReason.text.toString().trim()
            if (reason.length < 5) {
                binding.etReason.error = "Alasan minimal 5 karakter."
                return@setOnClickListener
            }
            if (startDate > endDate) {
                binding.tvFeedback.text = "Tanggal akhir tidak boleh sebelum tanggal awal."
                return@setOnClickListener
            }
            viewModel.create(childId, startDate, endDate, reason)
        }
        binding.btnReload.setOnClickListener {
            selectedChildId?.let(viewModel::loadRequests)
        }
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
                    binding.progressBar.visibility = if (state.loading || state.saving) View.VISIBLE else View.GONE
                    binding.btnSubmit.isEnabled = !state.loading && !state.saving && selectedChildId != null
                    binding.tvFeedback.text = state.error ?: state.message ?: if (!state.loading && state.children.isEmpty()) "Belum ada anak terhubung." else ""
                    val ids = state.children.map { it.id }
                    if (ids != lastChildIds) {
                        lastChildIds = ids
                        childIds = ids
                        binding.spinnerChild.adapter = ArrayAdapter(
                            requireContext(), android.R.layout.simple_spinner_dropdown_item,
                            state.children.map { "${it.name} (${it.nis ?: "-"})" }
                        )
                        if (ids.isNotEmpty()) selectedChildId = ids.first()
                    }
                    binding.listRequests.removeAllViews()
                    state.requests.forEach { request ->
                        val row = TextView(requireContext()).apply {
                            val label = when (request.status) {
                                "PENDING" -> "Menunggu"
                                "APPROVED" -> "Disetujui"
                                "REJECTED" -> "Ditolak"
                                else -> request.status
                            }
                            text = "${request.studentName} | ${request.startDate} s.d. ${request.endDate}\n${request.reason}\nStatus: $label"
                            setPadding(16, 18, 16, 18)
                            setBackgroundColor(android.graphics.Color.WHITE)
                            textSize = 14f
                        }
                        binding.listRequests.addView(row)
                    }
                    binding.tvEmpty.visibility = if (!state.loading && state.requests.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun pickDate(current: String, onChosen: (String) -> Unit) {
        val pieces = current.split('-').map(String::toInt)
        DatePickerDialog(requireContext(), { _, year, month, day ->
            onChosen(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
        }, pieces[0], pieces[1] - 1, pieces[2]).show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    companion object {
        private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(Calendar.getInstance().time)
    }
}
