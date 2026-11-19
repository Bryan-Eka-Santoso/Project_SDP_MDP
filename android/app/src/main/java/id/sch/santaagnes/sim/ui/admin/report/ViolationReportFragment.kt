package id.sch.santaagnes.sim.ui.admin.report

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.ViolationReportDto
import id.sch.santaagnes.sim.databinding.FragmentViolationReportBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class ViolationReportFragment : Fragment() {

    private var _binding: FragmentViolationReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ViolationReportViewModel by viewModels()

    private var selectedTypeId: Int? = null
    private var dateFrom: String? = null
    private var dateTo: String? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            viewModel.export {
                requireContext().contentResolver.openOutputStream(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViolationReportBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.etDateFrom.setOnClickListener {
            pilihTanggal(true)
        }

        binding.etDateTo.setOnClickListener {
            pilihTanggal(false)
        }

        binding.btnFilter.setOnClickListener {
            terapkanFilter()
        }

        binding.btnReset.setOnClickListener {
            selectedTypeId = null
            dateFrom = null
            dateTo = null

            binding.etType.setText("Semua", false)
            binding.etClassId.setText("")
            binding.etDateFrom.setText("")
            binding.etDateTo.setText("")

            viewModel.setFilter(null, null, null, null)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.refresh()
        }

        binding.btnLoadMore.setOnClickListener {
            viewModel.loadMore()
        }

        binding.btnExport.setOnClickListener {
            val filename = "laporan-pelanggaran-${
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(Calendar.getInstance().time)
            }.xlsx"

            exportLauncher.launch(filename)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.exporting

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null && !state.loading

                    binding.btnExport.isEnabled =
                        !state.exporting

                    binding.tvTotal.text = state.total.toString()

                    binding.tvTotalPoints.text =
                        state.items.sumOf { it.points }.toString()

                    binding.tvLoaded.text =
                        "Menampilkan ${state.items.size} dari ${state.total} kejadian"

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.items.isEmpty()

                    binding.btnLoadMore.isVisible =
                        !state.loading &&
                                state.page < state.totalPages &&
                                state.error == null

                    setupJenisPelanggaran()
                    tampilkanLaporan(state.items)

                    state.message?.let {
                        viewModel.clearMessage()

                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage(it)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        viewModel.loadTypes()
        viewModel.refresh()
    }

    private fun setupJenisPelanggaran() {
        val types = viewModel.state.value.types

        val labels = listOf("Semua") + types.map {
            it.name
        }

        binding.etType.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
        )

        binding.etType.setOnItemClickListener { _, _, position, _ ->
            selectedTypeId =
                if (position == 0) null
                else types[position - 1].id
        }
    }

    private fun pilihTanggal(start: Boolean) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val value = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    year,
                    month + 1,
                    day
                )

                if (start) {
                    dateFrom = value
                    binding.etDateFrom.setText(value)
                } else {
                    dateTo = value
                    binding.etDateTo.setText(value)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun terapkanFilter() {
        val classText = binding.etClassId.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val classId = if (classText.isEmpty()) {
            null
        } else {
            classText.toIntOrNull()
        }

        if (classText.isNotEmpty() &&
            (classId == null || classId <= 0)) {
            binding.etClassId.error = "ID kelas tidak valid"
            return
        }

        if (dateFrom != null && dateTo != null &&
            dateFrom!! > dateTo!!) {
            binding.etDateTo.error =
                "Tanggal akhir harus setelah tanggal awal"
            return
        }

        viewModel.setFilter(
            classGroupId = classId,
            violationTypeId = selectedTypeId,
            dateFrom = dateFrom,
            dateTo = dateTo
        )
    }

    private fun tampilkanLaporan(
        items: List<ViolationReportDto>
    ) {
        binding.listContainer.removeAllViews()

        items.forEach { item ->
            val card = MaterialCardView(requireContext()).apply {
                radius = dp(12).toFloat()
                cardElevation = dp(1).toFloat()

                setCardBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.white
                    )
                )

                layoutParams = LinearLayout.LayoutParams(
                    -1, -2
                ).apply {
                    bottomMargin = dp(12)
                }
            }

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }

            content.addView(
                buatText(item.studentName, 16f, true)
            )

            content.addView(
                buatText(
                    "NIS ${item.nis} • ${item.className}"
                )
            )

            content.addView(
                buatText(
                    "Tanggal: ${item.date}"
                )
            )

            content.addView(
                buatText(
                    "Pelanggaran: ${item.violationTypeName}"
                )
            )

            content.addView(
                buatText(
                    "Poin: ${item.points}",
                    14f,
                    true
                )
            )

            content.addView(
                buatText(
                    "Guru Pelapor: ${item.reporterName}"
                )
            )

            if (!item.description.isNullOrBlank()) {
                content.addView(
                    buatText("Keterangan: ${item.description}")
                )
            }

            card.addView(content)
            binding.listContainer.addView(card)
        }
    }

    private fun buatText(
        value: String,
        size: Float = 12f,
        bold: Boolean = false
    ): TextView {
        return TextView(requireContext()).apply {
            text = value
            textSize = size

            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.agnesian_text
                )
            )

            if (bold) {
                setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
                )
            }

            setPadding(0, 0, 0, dp(7))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
