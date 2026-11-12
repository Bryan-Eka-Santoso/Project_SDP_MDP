package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.BillDto
import id.sch.santaagnes.sim.data.remote.BillImportDto
import id.sch.santaagnes.sim.domain.model.Bill
import id.sch.santaagnes.sim.domain.model.BillImportResult
import id.sch.santaagnes.sim.domain.model.BillStatus
import id.sch.santaagnes.sim.domain.model.BillStudent
import java.time.LocalDate

fun BillDto.toDomain(): Bill = Bill(
    id = id,
    code = billCode,
    paymentType = paymentType,
    amount = amount,
    dueDate = LocalDate.parse(dueDate),
    paidDate = paidDate?.let(LocalDate::parse),
    status = when {
        status == "PAID" -> BillStatus.PAID
        isOverdue -> BillStatus.OVERDUE
        else -> BillStatus.UNPAID
    },
    student = student?.let {
        BillStudent(
            id = it.id,
            nis = it.nis,
            name = it.name,
            classNames = it.classGroups.orEmpty().map { group -> group.name }
        )
    }
)

fun BillImportDto.toDomain(): BillImportResult = BillImportResult(created, updated, unchanged, notifiedStudents)
