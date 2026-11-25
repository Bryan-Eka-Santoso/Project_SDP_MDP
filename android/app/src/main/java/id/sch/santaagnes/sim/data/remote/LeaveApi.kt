package id.sch.santaagnes.sim.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody

data class LeaveChildDto(
    val id: Int, val name: String, val nis: String?, val relationship: String?
)
data class LeaveRequestDto(
    val id: Int, val studentId: Int, val studentName: String,
    val startDate: String, val endDate: String, val reason: String,
    val status: String, val attachmentUrl: String?,
    val rejectionReason: String? = null
)
data class CreateLeaveDto(
    val studentId: Int, val startDate: String, val endDate: String, val reason: String
)
data class LeavePageDto<T>(
    val success: Boolean, val data: List<T>?, val meta: LeaveMetaDto?
)
data class LeaveMetaDto(val page: Int, val limit: Int, val total: Int, val totalPages: Int)
interface LeaveApi {
    // Download terautentikasi lewat Retrofit + interceptor token yang sudah ada.
    @Streaming
    @GET("homeroom/leave-requests/{requestId}/attachment")
    suspend fun downloadHomeroomAttachment(@Path("requestId") requestId: Int): ResponseBody

    @GET("me/children")
    suspend fun children(): ApiResponse<List<LeaveChildDto>>

    @GET("leave-requests")
    suspend fun list(@Query("studentId") studentId: Int, @Query("page") page: Int = 1): LeavePageDto<LeaveRequestDto>

    // IZN-03: daftar izin pending untuk guru wali kelas.
    @GET("homeroom/leave-requests/pending")
    suspend fun pendingHomeroom(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<HomeroomLeaveDto>>

    // IZN-04: keputusan wali kelas untuk pengajuan izin.
    @PATCH("homeroom/leave-requests/{requestId}/decision")
    suspend fun decideHomeroom(
        @Path("requestId") requestId: Int,
        @Body request: HomeroomDecisionRequest
    ): ApiResponse<Any>

    @POST("leave-requests")
    suspend fun create(@Body request: CreateLeaveDto): ApiResponse<LeaveRequestDto>

    @Multipart
    @POST("leave-requests")
    suspend fun createWithAttachment(
        @Part("studentId") studentId: RequestBody,
        @Part("startDate") startDate: RequestBody,
        @Part("endDate") endDate: RequestBody,
        @Part("reason") reason: RequestBody,
        @Part attachment: MultipartBody.Part
    ): ApiResponse<LeaveRequestDto>
}
