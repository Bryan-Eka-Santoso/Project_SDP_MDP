package id.sch.santaagnes.sim.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class StudentUserDto(
    val id: Int,
    val name: String,
    val email: String,
    val status: String
)

data class StudentClassGroupDto(
    val id: Int,
    val name: String,
    val gradeLevel: String? = null
)

data class StudentGuardianItemDto(
    val guardianId: Int,
    val relationship: String?,
    val name: String,
    val phone: String?,
    val email: String
)

data class StudentItemDto(
    val id: Int,
    val nis: String,
    val birthDate: String?,
    val phone: String?,
    val user: StudentUserDto,
    val currentClass: StudentClassGroupDto?,
    val guardians: List<StudentGuardianItemDto>?
)

data class CreateStudentRequest(
    val email: String,
    val password: String,
    val name: String,
    val nis: String,
    val birthDate: String? = null,
    val phone: String? = null,
    val classGroupId: Int? = null
)

data class UpdateStudentRequest(
    val name: String? = null,
    val nis: String? = null,
    val birthDate: String? = null,
    val phone: String? = null
)

data class LinkGuardianRequest(
    val guardianId: Int,
    val relationship: String
)

interface StudentApi {

    @GET("students")
    suspend fun getStudents(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("q") search: String? = null,
        @Query("classGroupId") classGroupId: Int? = null
    ): ApiResponse<List<StudentItemDto>>

    @GET("students/{id}")
    suspend fun getStudentById(
        @Path("id") id: Int
    ): ApiResponse<StudentItemDto>

    @POST("students")
    suspend fun createStudent(
        @Body body: CreateStudentRequest
    ): ApiResponse<StudentItemDto>

    @PATCH("students/{id}")
    suspend fun updateStudent(
        @Path("id") id: Int,
        @Body body: UpdateStudentRequest
    ): ApiResponse<StudentItemDto>

    @DELETE("students/{id}")
    suspend fun deleteStudent(
        @Path("id") id: Int
    ): Response<Unit>

    @POST("students/{id}/guardians")
    suspend fun linkGuardian(
        @Path("id") id: Int,
        @Body body: LinkGuardianRequest
    ): ApiResponse<Unit>

    @DELETE("students/{id}/guardians/{guardianId}")
    suspend fun unlinkGuardian(
        @Path("id") id: Int,
        @Path("guardianId") guardianId: Int
    ): Response<Unit>
}
