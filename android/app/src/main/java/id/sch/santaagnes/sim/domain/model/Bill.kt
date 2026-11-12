package id.sch.santaagnes.sim.domain.model

import java.time.LocalDate

/** Status tampil: "Menunggak" = belum dibayar dan sudah lewat jatuh tempo (aturan di backend). */
enum class BillStatus { PAID, OVERDUE, UNPAID }

/** Siswa pemilik tagihan pada daftar lintas siswa. */
data class BillStudent(
    val id: Int,
    val nis: String?,
    val name: String,
    val classNames: List<String>
)

data class Bill(
    val id: Int,
    val code: String,
    val paymentType: String,
    // rupiah bulat
    val amount: Long,
    val dueDate: LocalDate,
    val paidDate: LocalDate?,
    val status: BillStatus,
    // null bila daftar milik satu siswa (identitasnya sudah jelas dari layar)
    val student: BillStudent?
)

/** Tagihan siapa yang dilihat (menentukan endpoint). */
sealed interface BillScope {
    /** Satu siswa: siswa sendiri atau anak yang dipilih wali murid. */
    data class Student(val studentId: Int) : BillScope

    /** Kelas binaan wali kelas. */
    data class HomeroomClass(val classGroupId: Int) : BillScope

    /** Semua siswa (admin). */
    data object All : BillScope
}

data class BillScopeOption(
    val scope: BillScope,
    // nama anak/kelas; null bila hanya ada satu pilihan yang jelas
    val name: String?
)

data class BillViewer(
    val role: String,
    val options: List<BillScopeOption>,
    val initialIndex: Int = 0
)

/** Penyaring status pada daftar tagihan. */
enum class BillFilter { ALL, OVERDUE, PAID }

data class BillImportResult(
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val notifiedStudents: Int
)
