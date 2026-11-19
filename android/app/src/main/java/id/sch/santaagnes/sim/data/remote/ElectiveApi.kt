package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class SubjectPackageSubjectDto(
    val id: Int,
    val code: String,
    val name: String,
    val type: String
)

data class SubjectPackageDto(
    val id: Int,
    val academicYearId: Int,
    val academicYear: AcademicYearSummaryDto? = null,
    val name: String,
    val capacity: Int,
    val subjects: List<SubjectPackageSubjectDto> = emptyList(),
    val selectionCount: Int = 0
)

data class CreateSubjectPackageRequest(
    val academicYearId: Int,
    val name: String,
    val capacity: Int,
    val subjectIds: List<Int>
)

data class UpdateSubjectPackageRequest(
    val name: String? = null,
    val capacity: Int? = null,
    val subjectIds: List<Int>? = null
)

interface ElectiveApi {

    @GET("subject-packages")
    suspend fun getSubjectPackages(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("academicYearId") academicYearId: Int? = null,
        @Query("q") search: String? = null
    ): ApiResponse<List<SubjectPackageDto>>

    @POST("subject-packages")
    suspend fun createSubjectPackage(
        @Body body: CreateSubjectPackageRequest
    ): ApiResponse<SubjectPackageDto>

    @PATCH("subject-packages/{id}")
    suspend fun updateSubjectPackage(
        @Path("id") id: Int,
        @Body body: UpdateSubjectPackageRequest
    ): ApiResponse<SubjectPackageDto>

    @DELETE("subject-packages/{id}")
    suspend fun deleteSubjectPackage(
        @Path("id") id: Int
    ): Response<Unit>
}
