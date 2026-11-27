package id.sch.santaagnes.sim.ui.admin.report

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Rupiah
import id.sch.santaagnes.sim.domain.model.Bill
import id.sch.santaagnes.sim.domain.model.BillStatus
import id.sch.santaagnes.sim.domain.model.PaymentStatusReportFilter
import id.sch.santaagnes.sim.ui.bill.BillAdapter

/** Laporan status pembayaran (F-LAP-05): filter kelas, status (lunas/belum dibayar/menunggak), dan jatuh tempo. */
@AndroidEntryPoint
class PaymentStatusReportFragment : ReportFragment<Bill, PaymentStatusReportFilter>() {

    override val viewModel: ReportViewModel<Bill, PaymentStatusReportFilter> by
        viewModels<PaymentStatusReportViewModel>()

    override val adapter = BillAdapter(horizontalMarginDp = 8)
    override val titleRes = R.string.report_payment_status_title
    override val reportType = "payment-status"
    override val totalRes = R.string.report_total_bills

    private val paymentViewModel get() = viewModel as PaymentStatusReportViewModel

    private val statuses: List<BillStatus?> = listOf(null, BillStatus.PAID, BillStatus.UNPAID, BillStatus.OVERDUE)
    private var classDropdown: ReportDropdown? = null

    override fun setupFilters() {
        binding.layoutTeacher.isVisible = false
        classDropdown = ReportDropdown(binding.inputClass) { paymentViewModel.selectClassGroup(it) }

        binding.scrollTypes.isVisible = true
        binding.chipTypes.removeAllViews()
        statuses.forEach { status ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(
                    when (status) {
                        null -> R.string.bill_filter_all
                        BillStatus.PAID -> R.string.bill_status_paid
                        BillStatus.UNPAID -> R.string.bill_status_unpaid
                        BillStatus.OVERDUE -> R.string.bill_status_overdue
                    }
                )
                isCheckable = true
                setOnClickListener { paymentViewModel.selectStatus(status) }
            }
            binding.chipTypes.addView(chip)
        }

        setupDateRange(
            fromHintRes = R.string.report_filter_due_from,
            toHintRes = R.string.report_filter_due_to,
            currentFrom = { paymentViewModel.uiState.value.filter.dueFrom },
            currentTo = { paymentViewModel.uiState.value.filter.dueTo },
            onDateFrom = { paymentViewModel.selectDateFrom(it) },
            onDateTo = { paymentViewModel.selectDateTo(it) },
            onClear = { paymentViewModel.clearDates() }
        )
    }

    override fun renderFilters(state: ReportUiState<Bill, PaymentStatusReportFilter>) {
        val filter = state.filter
        (binding.chipTypes.getChildAt(statuses.indexOf(filter.status)) as? Chip)?.isChecked = true

        val classes = state.options.classGroups
        binding.filterRow.isVisible = classes.isNotEmpty()
        classDropdown?.render(
            listOf<Pair<Int?, String>>(null to getString(R.string.admin_filter_all_classes)) +
                classes.map { it.id to it.name },
            filter.classGroupId
        )

        renderDateRange(filter.dueFrom, filter.dueTo)
    }

    override fun buildChartSpec(state: ReportUiState<Bill, PaymentStatusReportFilter>): ReportChartSpec {
        val ctx = requireContext()
        val paid = state.rows.filter { it.status == BillStatus.PAID }
        val unpaid = state.rows.filter { it.status == BillStatus.UNPAID }
        val overdue = state.rows.filter { it.status == BillStatus.OVERDUE }
        val paidSum = paid.sumOf { it.amount }
        val overdueSum = overdue.sumOf { it.amount }
        val cPaid = ContextCompat.getColor(ctx, R.color.bill_paid)
        val cUnpaid = ContextCompat.getColor(ctx, R.color.change_substitute)
        val cOverdue = ContextCompat.getColor(ctx, R.color.change_cancel)
        return ReportChartSpec(
            title = "Ringkasan Status Pembayaran",
            subtitle = "Berdasarkan ${state.rows.size} tagihan yang ditampilkan",
            kind = ReportChartKind.DONUT,
            centerLabel = "${state.rows.size}",
            centerSubLabel = "Tagihan",
            kpis = listOf(
                ChartKpi("Total Lunas", Rupiah.format(paidSum), cPaid),
                ChartKpi("Total Menunggak", Rupiah.format(overdueSum), cOverdue)
            ),
            segments = listOf(
                ChartSegment("Lunas", paid.size.toFloat(), "${paid.size} tagihan", cPaid),
                ChartSegment("Belum dibayar", unpaid.size.toFloat(), "${unpaid.size} tagihan", cUnpaid),
                ChartSegment("Menunggak", overdue.size.toFloat(), "${overdue.size} tagihan", cOverdue)
            )
        )
    }

    override fun buildExportPreview(
        state: ReportUiState<Bill, PaymentStatusReportFilter>,
        fileName: String
    ): ReportExportPreviewSpec {
        val filters = buildList {
            state.filter.classGroupId?.let { id ->
                state.options.classGroups.firstOrNull { it.id == id }?.name?.let { add("Kelas: $it") }
            }
            state.filter.status?.let {
                add(
                    "Status: " + when (it) {
                        BillStatus.PAID -> "Lunas"
                        BillStatus.UNPAID -> "Belum dibayar"
                        BillStatus.OVERDUE -> "Menunggak"
                    }
                )
            }
            if (state.filter.dueFrom != null || state.filter.dueTo != null) {
                add(
                    "Jatuh tempo: ${state.filter.dueFrom?.let(DateTimeWib::formatDate) ?: "..."} – " +
                        (state.filter.dueTo?.let(DateTimeWib::formatDate) ?: "...")
                )
            }
        }
        return ReportExportPreviewSpec(
            reportTitle = getString(titleRes),
            fileName = fileName,
            totalCount = state.total,
            filters = filters,
            columns = listOf("Siswa", "Kode", "Jenis", "Nominal", "Jatuh Tempo", "Status"),
            rows = state.rows.map { bill ->
                listOf(
                    bill.student?.name.orEmpty().ifBlank { "—" },
                    bill.code,
                    bill.paymentType,
                    Rupiah.format(bill.amount),
                    DateTimeWib.formatDate(bill.dueDate),
                    when (bill.status) {
                        BillStatus.PAID -> "Lunas"
                        BillStatus.UNPAID -> "Belum dibayar"
                        BillStatus.OVERDUE -> "Menunggak"
                    }
                )
            }
        )
    }

    override fun releaseFilters() {
        classDropdown = null
    }
}
