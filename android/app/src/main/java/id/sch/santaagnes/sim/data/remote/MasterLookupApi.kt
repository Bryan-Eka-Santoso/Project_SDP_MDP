package id.sch.santaagnes.sim.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

// Kontrak endpoint milik Yosua (F-MST-06, F-MST-10), dibaca dari branch origin/yosua. Hanya dibaca di
// sini; bila bentuknya berubah, sesuaikan DTO ini. Berlaku setelah branch itu masuk ke main.

data class LookupSemesterDto(
    val id: Int
)

data class LookupTeacherUserDto(
    val name: String
)

data class LookupTeacherDto(
    val id: Int,
    val user: LookupTeacherUserDto
)

data class LookupAssignmentDto(
    val id: Int,
    val teacher: LookupTeacherDto,
    val subject: SubjectDto,
    val classGroup: IdNameDto
)

interface MasterLookupApi {

    @GET("semesters/active")
    suspend fun getActiveSemester(): ApiResponse<LookupSemesterDto>

    // Semua guru (F-MST-05), termasuk yang belum punya penugasan; dipakai filter laporan.
    @GET("teachers")
    suspend fun getTeachers(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): ApiResponse<List<LookupTeacherDto>>

    @GET("teaching-assignments")
    suspend fun getTeachingAssignments(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("semesterId") semesterId: Int
    ): ApiResponse<List<LookupAssignmentDto>>
}
