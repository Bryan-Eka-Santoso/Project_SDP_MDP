package id.sch.santaagnes.sim.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

data class SlotAssignmentDto(
    val id: Int,
    val teacher: IdNameDto,
    val subject: SubjectDto,
    val classGroup: IdNameDto
)

/** Slot jadwal mingguan (master jadwal, F-JDW-01). Jam berformat `HH:mm`, hari `MONDAY`..`SATURDAY`. */
data class SlotDto(
    val id: Int,
    val semesterId: Int,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val room: IdNameDto?,
    val teachingAssignment: SlotAssignmentDto
)

/** Perubahan jadwal pada satu tanggal (F-JDW-04). */
data class ChangeDto(
    val id: Int,
    val scheduleSlotId: Int,
    val date: String,
    val type: String,
    val substituteTeacher: IdNameDto?,
    val newRoom: IdNameDto?,
    val newStartTime: String?,
    val newEndTime: String?,
    val description: String,
    val slot: SlotDto
)

// Gson tidak menulis field null, sehingga kolom opsional yang kosong tidak ikut terkirim.
data class SlotRequest(
    val teachingAssignmentId: Int,
    val roomId: Int,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String
)

data class SaveChangeRequest(
    val scheduleSlotId: Int,
    val date: String,
    val type: String,
    val description: String,
    val substituteTeacherId: Int? = null,
    val newRoomId: Int? = null,
    val newStartTime: String? = null,
    val newEndTime: String? = null
)

data class ImportResultDto(
    val semesterId: Int,
    val imported: Int
)

interface AdminScheduleApi {

    // Tanpa semesterId server memakai semester aktif.
    @GET("schedule-slots")
    suspend fun getSlots(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("classGroupId") classGroupId: Int?,
        @Query("teacherId") teacherId: Int?,
        @Query("dayOfWeek") dayOfWeek: String?
    ): ApiResponse<List<SlotDto>>

    @POST("schedule-slots")
    suspend fun createSlot(@Body body: SlotRequest): ApiResponse<SlotDto>

    @PATCH("schedule-slots/{id}")
    suspend fun updateSlot(@Path("id") id: Int, @Body body: SlotRequest): ApiResponse<SlotDto>

    @DELETE("schedule-slots/{id}")
    suspend fun deleteSlot(@Path("id") id: Int): Response<Unit>

    // semesterId (opsional) dikirim sebagai field form; dihilangkan = semester aktif.
    @Multipart
    @POST("schedule-slots/import")
    suspend fun importSlots(@Part file: MultipartBody.Part): ApiResponse<ImportResultDto>

    @GET("schedule-changes")
    suspend fun getChanges(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("type") type: String?
    ): ApiResponse<List<ChangeDto>>

    // Mengirim ulang untuk slot + tanggal yang sama berarti mengubah perubahan itu.
    @POST("schedule-changes")
    suspend fun saveChange(@Body body: SaveChangeRequest): ApiResponse<ChangeDto>

    @DELETE("schedule-changes/{id}")
    suspend fun deleteChange(@Path("id") id: Int): Response<Unit>
}
