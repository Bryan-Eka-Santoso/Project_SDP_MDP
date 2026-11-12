package id.sch.santaagnes.sim.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class IdNameDto(
    val id: Int,
    val name: String
)

data class SubjectDto(
    val id: Int,
    val code: String?,
    val name: String
)

data class ScheduleChangeDto(
    val id: Int,
    val type: String,
    val description: String?
)

data class ScheduleOriginalDto(
    val startTime: String,
    val endTime: String,
    val room: IdNameDto?,
    val teacher: IdNameDto?
)

/** Satu pelajaran pada satu tanggal (F-JDW-05); jam, ruang, dan guru sudah memperhitungkan perubahan. */
data class ScheduleEntryDto(
    val date: String,
    val slotId: Int,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: IdNameDto?,
    val teacher: IdNameDto,
    val subject: SubjectDto,
    val classGroup: IdNameDto,
    val change: ScheduleChangeDto?,
    val original: ScheduleOriginalDto?
)

interface ScheduleApi {

    // studentId hanya untuk wali murid, classGroupId hanya untuk wali kelas; siswa dan guru
    // cukup mengirim rentang tanggal (maksimal 31 hari).
    @GET("schedule")
    suspend fun getSchedule(
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Query("studentId") studentId: Int?,
        @Query("classGroupId") classGroupId: Int?
    ): ApiResponse<List<ScheduleEntryDto>>
}
