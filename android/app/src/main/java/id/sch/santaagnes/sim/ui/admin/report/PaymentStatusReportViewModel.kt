package id.sch.santaagnes.sim.ui.admin.report

import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.domain.model.Bill
import id.sch.santaagnes.sim.domain.model.BillStatus
import id.sch.santaagnes.sim.domain.model.PaymentStatusReportFilter
import id.sch.santaagnes.sim.domain.model.ReportOptions
import id.sch.santaagnes.sim.domain.model.ReportPage
import java.time.LocalDate
import javax.inject.Inject

/** Laporan status pembayaran (F-LAP-05). */
@HiltViewModel
class PaymentStatusReportViewModel @Inject constructor(
    private val repository: ReportRepository
) : ReportViewModel<Bill, PaymentStatusReportFilter>(PaymentStatusReportFilter()) {

    override suspend fun fetch(page: Int, filter: PaymentStatusReportFilter): Resource<ReportPage<Bill>> =
        repository.paymentStatus(page, filter)

    override suspend fun export(filter: PaymentStatusReportFilter, destination: String): Resource<Boolean> =
        repository.exportPaymentStatus(filter, destination)

    override suspend fun fetchOptions(): Resource<ReportOptions> = repository.options()

    override fun idOf(row: Bill): Int = row.id

    fun selectClassGroup(classGroupId: Int?) = updateFilter { it.copy(classGroupId = classGroupId) }

    fun selectStatus(status: BillStatus?) = updateFilter { it.copy(status = status) }

    fun selectDateFrom(date: LocalDate?) = updateFilter {
        val (from, to) = adjustDateFrom(date, it.dueTo)
        it.copy(dueFrom = from, dueTo = to)
    }

    fun selectDateTo(date: LocalDate?) = updateFilter {
        val (from, to) = adjustDateTo(it.dueFrom, date)
        it.copy(dueFrom = from, dueTo = to)
    }

    fun clearDates() = updateFilter { it.copy(dueFrom = null, dueTo = null) }
}
