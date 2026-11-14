package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.core.Rupiah
import id.sch.santaagnes.sim.data.remote.BillDto
import id.sch.santaagnes.sim.data.remote.BillStudentDto
import id.sch.santaagnes.sim.data.remote.IdNameDto
import id.sch.santaagnes.sim.data.repository.toDomain
import id.sch.santaagnes.sim.domain.model.BillStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BillMappersTest {

    private val dto = BillDto(
        id = 1,
        billCode = "SPP-2026-11",
        paymentType = "SPP",
        amount = 850_000,
        dueDate = "2026-11-15",
        status = "UNPAID",
        paidDate = null,
        isOverdue = true
    )

    @Test
    fun unpaidAndOverdueIsOverdue() {
        assertEquals(BillStatus.OVERDUE, dto.toDomain().status)
    }

    @Test
    fun unpaidNotYetDueIsJustUnpaid() {
        assertEquals(BillStatus.UNPAID, dto.copy(isOverdue = false).toDomain().status)
    }

    @Test
    fun paidIsPaidEvenWithPaymentDate() {
        val bill = dto.copy(status = "PAID", paidDate = "2026-11-10").toDomain()

        assertEquals(BillStatus.PAID, bill.status)
        assertEquals(LocalDate.of(2026, 11, 10), bill.paidDate)
    }

    @Test
    fun studentListHasNoStudentInfo() {
        assertNull(dto.toDomain().student)
    }

    @Test
    fun mapsStudentOnCrossStudentList() {
        val bill = dto.copy(
            student = BillStudentDto(41, "2026001", "Siswa Satu", listOf(IdNameDto(5, "X-1"), IdNameDto(9, "Paket A")))
        ).toDomain()

        assertEquals("Siswa Satu", bill.student?.name)
        assertEquals("2026001", bill.student?.nis)
        assertEquals(listOf("X-1", "Paket A"), bill.student?.classNames)
    }

    @Test
    fun formatsRupiahWithDotSeparators() {
        assertEquals("Rp 0", Rupiah.format(0))
        assertEquals("Rp 500", Rupiah.format(500))
        assertEquals("Rp 1.000", Rupiah.format(1_000))
        assertEquals("Rp 850.000", Rupiah.format(850_000))
        assertEquals("Rp 9.999.999.999", Rupiah.format(9_999_999_999))
    }
}
