package id.sch.santaagnes.sim.data.remote

data class HomeroomLeaveDto(
    val id: Int,
    val studentId: Int,
    val studentName: String,
    val nis: String?,
    val startDate: String,
    val endDate: String,
    val reason: String,
    val attachmentUrl: String?,
    val status: String,
    val createdAt: String?,
    val rejectionReason: String? = null
)

/** Nilai decision harus APPROVED atau REJECTED sesuai validasi backend; rejectionReason wajib saat REJECTED. */
data class HomeroomDecisionRequest(
    val decision: String,
    val rejectionReason: String? = null
)
