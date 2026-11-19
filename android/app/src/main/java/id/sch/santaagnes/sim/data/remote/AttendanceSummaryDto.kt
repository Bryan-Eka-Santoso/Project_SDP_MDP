package id.sch.santaagnes.sim.data.remote

data class AttendanceSummaryDto(
    val studentId: Int,
    val total: Int,
    val counts: AttendanceCountsDto,
    val attendancePercentage: Double?,
    val entries: List<AttendanceSummaryEntryDto>
)

data class AttendanceCountsDto(
    val PRESENT: Int,
    val SICK: Int,
    val EXCUSED: Int,
    val ABSENT: Int
)

data class AttendanceSummaryEntryDto(
    val date: String,
    val status: String,
    val scheduleSlotId: Int,
    val teachingAssignmentId: Int,
    val subjectName: String,
    val className: String
)