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

data class AnnouncementAuthorDto(
    val id: Int,
    val name: String,
    val role: String
)

data class AnnouncementDto(
    val id: Int,
    val title: String,
    val content: String,
    val scope: String,
    val classGroupId: Int? = null,
    val classGroup: IdNameDto? = null,
    val author: AnnouncementAuthorDto? = null,
    val date: String,
    val attachmentUrl: String? = null,
    val attachmentPublicId: String? = null,
    val createdAt: String? = null
)

data class UploadedAttachmentDto(
    val url: String,
    val publicId: String,
    val fileName: String
)

data class CreateAnnouncementRequest(
    val title: String,
    val content: String,
    val scope: String,
    val classGroupId: Int? = null,
    val date: String? = null,
    val attachmentUrl: String? = null,
    val attachmentPublicId: String? = null
)

data class UpdateAnnouncementRequest(
    val title: String? = null,
    val content: String? = null,
    val date: String? = null,
    val attachmentUrl: String? = null,
    val attachmentPublicId: String? = null
)

data class AcademicEventDto(
    val id: Int,
    val title: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val description: String? = null
)

data class CreateAcademicEventRequest(
    val title: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val description: String? = null
)

data class UpdateAcademicEventRequest(
    val title: String? = null,
    val type: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val description: String? = null
)

interface SchoolInfoApi {

    @GET("announcements")
    suspend fun getAnnouncements(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("scope") scope: String? = null,
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("studentId") studentId: Int? = null,
        @Query("q") search: String? = null
    ): ApiResponse<List<AnnouncementDto>>

    @Multipart
    @POST("announcements/attachment")
    suspend fun uploadAnnouncementAttachment(
        @Part file: MultipartBody.Part
    ): ApiResponse<UploadedAttachmentDto>

    @POST("announcements")
    suspend fun createAnnouncement(
        @Body body: CreateAnnouncementRequest
    ): ApiResponse<AnnouncementDto>

    @PATCH("announcements/{id}")
    suspend fun updateAnnouncement(
        @Path("id") id: Int,
        @Body body: UpdateAnnouncementRequest
    ): ApiResponse<AnnouncementDto>

    @DELETE("announcements/{id}")
    suspend fun deleteAnnouncement(
        @Path("id") id: Int
    ): Response<Unit>

    @GET("academic-events")
    suspend fun getAcademicEvents(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("q") search: String? = null
    ): ApiResponse<List<AcademicEventDto>>

    @POST("academic-events")
    suspend fun createAcademicEvent(
        @Body body: CreateAcademicEventRequest
    ): ApiResponse<AcademicEventDto>

    @PATCH("academic-events/{id}")
    suspend fun updateAcademicEvent(
        @Path("id") id: Int,
        @Body body: UpdateAcademicEventRequest
    ): ApiResponse<AcademicEventDto>

    @DELETE("academic-events/{id}")
    suspend fun deleteAcademicEvent(
        @Path("id") id: Int
    ): Response<Unit>
}
