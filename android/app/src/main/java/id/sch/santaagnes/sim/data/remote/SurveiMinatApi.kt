package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class TahunSurveiDto(
    val id: Int,
    val name: String,
    val isActive: Boolean
)

data class MapelSurveiDto(
    val id: Int,
    val code: String,
    val name: String
)

data class SurveiLookupsDto(
    val academicYears: List<TahunSurveiDto>,
    val subjects: List<MapelSurveiDto>
)

data class SurveiMinatDto(
    val id: Int,
    val academicYearId: Int,
    val academicYearName: String?,
    val gradeLevel: String,
    val title: String,
    val openDate: String,
    val closeDate: String,
    val status: String,
    val responseCount: Int
)

data class BuatSurveiRequest(
    val academicYearId: Int,
    val gradeLevel: String,
    val title: String,
    val openDate: String,
    val closeDate: String
)

data class RekapMapelDto(
    val id: Int,
    val code: String,
    val name: String,
    val studentCount: Int
)

data class RekapSurveiDto(
    val survey: SurveiMinatDto,
    val respondentCount: Int,
    val selectionCount: Int,
    val subjects: List<RekapMapelDto>
)

interface SurveiMinatApi {

    @GET("elective-surveys/lookups")
    suspend fun lookups(): ApiResponse<SurveiLookupsDto>

    @GET("elective-surveys")
    suspend fun list(): ApiResponse<List<SurveiMinatDto>>

    @POST("elective-surveys")
    suspend fun create(
        @Body body: BuatSurveiRequest
    ): ApiResponse<SurveiMinatDto>

    @POST("elective-surveys/{id}/publish")
    suspend fun publish(
        @Path("id") id: Int
    ): ApiResponse<SurveiMinatDto>

    @POST("elective-surveys/{id}/close")
    suspend fun close(
        @Path("id") id: Int
    ): ApiResponse<SurveiMinatDto>

    @GET("elective-surveys/{id}/recap")
    suspend fun recap(
        @Path("id") id: Int
    ): ApiResponse<RekapSurveiDto>
}
