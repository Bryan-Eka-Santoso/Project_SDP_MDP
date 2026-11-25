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
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.remote.FacilityBookingReportDto
import id.sch.santaagnes.sim.databinding.FragmentFacilityBookingReportBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class FacilityBookingReportFragment : Fragment() {

    private var _binding: FragmentFacilityBookingReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FacilityBookingReportViewModel by viewModels()

    private var selectedStatus: String? = null
    private var dateFrom: String? = null
    private var dateTo: String? = null

    private var renderedItems: List<FacilityBookingReportDto>? = null

    private val statuses = listOf(
        null,
        "PENDING",
        "APPROVED",
        "REJECTED",
        "RETURNED"
    )

    private val statusLabels = listOf(
        "Semua Status",
        "Menunggu",
        "Disetujui",
        "Ditolak",
        "Dikembalikan"
    )

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            val resolver = requireContext().contentResolver

            viewModel.export {
                resolver.openOutputStream(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFacilityBookingReportBinding.inflate(
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

        setupStatus()

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
            selectedStatus = null
            dateFrom = null
            dateTo = null

            binding.etStatus.setText("Semua Status", false)
            binding.etFacilityId.setText("")
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
            val tanggal = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(Calendar.getInstance().time)

            exportLauncher.launch(
                "laporan-peminjaman-$tanggal.xlsx"
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.exporting

                    binding.btnExport.isEnabled =
                        !state.exporting && !state.loading

                    binding.tvError.isVisible =
                        state.error != null
                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null && !state.loading

                    binding.tvTotal.text = state.total.toString()

                    binding.tvLoaded.text =
                        "Menampilkan ${state.items.size} dari ${state.total} peminjaman"

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.items.isEmpty()

                    binding.btnLoadMore.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.page < state.totalPages

                    if (renderedItems != state.items) {
                        renderedItems = state.items
                        tampilkanLaporan(state.items)
                    }

                    state.message?.let { message ->
                        viewModel.clearMessage()

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Berhasil")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        viewModel.refresh()
    }

    private fun setupStatus() {
        binding.etStatus.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                statusLabels
            )
        )

        binding.etStatus.setText("Semua Status", false)

        binding.etStatus.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = statuses[position]
        }
    }

    private fun pilihTanggal(isStart: Boolean) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val tanggal = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    year,
                    month + 1,
                    day
                )

                if (isStart) {
                    dateFrom = tanggal
                    binding.etDateFrom.setText(tanggal)
                } else {
                    dateTo = tanggal
                    binding.etDateTo.setText(tanggal)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun terapkanFilter() {
        val facilityText = binding.etFacilityId.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val facilityId = facilityText.toIntOrNull()

        if (
            facilityText.isNotEmpty() &&
            (facilityId == null || facilityId <= 0)
        ) {
            binding.etFacilityId.error =
                "ID fasilitas harus angka positif"
            return
        }

        if (
            dateFrom != null &&
            dateTo != null &&
            dateFrom!! > dateTo!!
        ) {
            binding.etDateTo.error =
                "Tanggal akhir tidak boleh sebelum tanggal awal"
            return
        }

        viewModel.setFilter(
            facilityId = facilityId,
            status = selectedStatus,
            dateFrom = dateFrom,
            dateTo = dateTo
        )
    }

    private fun tampilkanLaporan(
        items: List<FacilityBookingReportDto>
    ) {
        binding.listContainer.removeAllViews()
        if (items.isNotEmpty()) {
            val ctx = requireContext()
            val pending = items.count { it.status == "PENDING" }
            val approved = items.count { it.status == "APPROVED" }
            val returned = items.count { it.status == "RETURNED" }
            val rejected = items.count { it.status == "REJECTED" }
            binding.listContainer.addView(
                ReportChartRenderer.buildCard(
                    ctx,
                    ReportChartSpec(
                        title = "Sebaran Status Peminjaman",
                        subtitle = "Berdasarkan ${items.size} peminjaman yang ditampilkan",
                        kind = ReportChartKind.DONUT,
                        centerLabel = "${items.size}",
                        centerSubLabel = "Pengajuan",
                        segments = listOf(
                            ChartSegment("Disetujui", approved.toFloat(), "$approved", ContextCompat.getColor(ctx, R.color.agnesian_blue)),
                            ChartSegment("Dikembalikan", returned.toFloat(), "$returned", ContextCompat.getColor(ctx, R.color.bill_paid)),
                            ChartSegment("Menunggu", pending.toFloat(), "$pending", ContextCompat.getColor(ctx, R.color.change_substitute)),
                            ChartSegment("Ditolak", rejected.toFloat(), "$rejected", ContextCompat.getColor(ctx, R.color.change_cancel))
                        )
                    )
                )
            )
        }

        items.forEach { item ->
            val card = MaterialCardView(requireContext()).apply {
                radius = dp(12).toFloat()
                cardElevation = dp(1).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            }

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }

            val statusColor = when (item.status) {
                "APPROVED" -> R.color.agnesian_blue
                "RETURNED" -> R.color.bill_paid
                "REJECTED" -> R.color.change_cancel
                else -> R.color.change_substitute
            }

            val header = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val titleCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val typeLabel = when (item.facilityType) {
                "ROOM" -> "Ruangan"
                "ITEM" -> "Barang"
                else -> item.facilityType
            }
            titleCol.addView(buatText(item.facilityName, 16f, true, R.color.agnesian_text))
            titleCol.addView(buatText("$typeLabel • ${item.requesterName}", 12f, false, R.color.agnesian_muted))
            header.addView(titleCol)
            header.addView(ReportChartRenderer.buildBadge(requireContext(), labelStatus(item.status), statusColor))
            content.addView(header)

            val formattedDate = runCatching {
                DateTimeWib.formatDate(java.time.LocalDate.parse(item.bookingDate))
            }.getOrDefault(item.bookingDate)
            content.addView(buatText("$formattedDate  •  ${item.startTime}–${item.endTime} WIB", 13f, true, R.color.agnesian_blue))
            content.addView(buatText("Keperluan: ${item.purpose}", 13f, false, R.color.agnesian_text))

            if (!item.rejectionReason.isNullOrBlank()) {
                content.addView(buatText("Alasan Penolakan: ${item.rejectionReason}", 13f, true, R.color.change_cancel))
            }
            if (item.verifierName != null) {
                content.addView(buatText("Diverifikasi oleh: ${item.verifierName}", 12f, false, R.color.agnesian_muted))
            }
            if (item.returnDate != null) {
                content.addView(buatText("Tanggal kembali: ${item.returnDate}", 12f, true, R.color.bill_paid))
            }

            card.addView(content)
            binding.listContainer.addView(card)
        }
    }

    private fun labelStatus(value: String): String {
        return when (value) {
            "PENDING" -> "Menunggu"
            "APPROVED" -> "Disetujui"
            "REJECTED" -> "Ditolak"
            "RETURNED" -> "Dikembalikan"
            else -> value
        }
    }

    private fun buatText(
        value: String,
        size: Float,
        bold: Boolean,
        colorRes: Int = R.color.agnesian_text
    ): TextView {
        return TextView(requireContext()).apply {
            text = value
            textSize = size
            setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        }
    }

    private fun dp(value: Int): Int {
        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroyView() {
        renderedItems = null
        _binding = null
        super.onDestroyView()
    }
}
