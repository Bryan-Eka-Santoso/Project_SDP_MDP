package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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
    val description: String?,
    // Pindah lintas hari: tanggal tujuan (pada entri tanggal asal) atau tanggal asal (pada entri pindahan).
    val movedTo: String? = null,
    val movedFrom: String? = null
)

/**
 * Guru memindah satu pertemuan dari slot mengajarnya (F-JDW-04). Gson tidak menulis field null,
 * jadi yang tidak diubah tidak ikut terkirim.
 */
data class RescheduleRequest(
    val scheduleSlotId: Int,
    val date: String,
    val description: String,
    val newDate: String? = null,
    val newStartTime: String? = null,
    val newEndTime: String? = null,
    val newRoomId: Int? = null,
    val type: String = "RESCHEDULE"
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

    // Pilihan ruangan untuk form pindah jadwal (guru dan admin).
    @GET("schedule-rooms")
    suspend fun getRooms(): ApiResponse<List<IdNameDto>>

    // Mengirim ulang untuk slot + tanggal yang sama berarti mengubah pemindahan itu.
    @POST("schedule-changes")
    suspend fun reschedule(@Body body: RescheduleRequest): ApiResponse<ScheduleChangeDto>

    @DELETE("schedule-changes/{id}")
    suspend fun deleteChange(@Path("id") id: Int): Response<Unit>
}
