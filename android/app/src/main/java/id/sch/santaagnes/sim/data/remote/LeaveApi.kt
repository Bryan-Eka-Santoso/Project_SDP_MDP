package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class LeaveChildDto(
    val id: Int, val name: String, val nis: String?, val relationship: String?
)
data class LeaveRequestDto(
    val id: Int, val studentId: Int, val studentName: String,
    val startDate: String, val endDate: String, val reason: String,
    val status: String, val attachmentUrl: String?
)
data class CreateLeaveDto(
    val studentId: Int, val startDate: String, val endDate: String, val reason: String
)
data class LeavePageDto<T>(
    val success: Boolean, val data: List<T>?, val meta: LeaveMetaDto?
)
data class LeaveMetaDto(val page: Int, val limit: Int, val total: Int, val totalPages: Int)
interface LeaveApi {
    @GET("me/children")
    suspend fun children(): ApiResponse<List<LeaveChildDto>>

    @GET("leave-requests")
    suspend fun list(@Query("studentId") studentId: Int, @Query("page") page: Int = 1): LeavePageDto<LeaveRequestDto>

    @POST("leave-requests")
    suspend fun create(@Body request: CreateLeaveDto): ApiResponse<LeaveRequestDto>
}
