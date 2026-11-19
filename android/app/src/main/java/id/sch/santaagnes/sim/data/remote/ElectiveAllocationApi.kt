package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** MPP-05: daftar paket diambil dari tren, lalu preview alokasi dihitung oleh backend. */
data class ElectiveTrendsDto(
    val academicYears: List<ElectiveYearDto> = emptyList(),
    val packages: List<ElectivePackageTrendDto> = emptyList()
)
data class ElectiveYearDto(val id: Int, val name: String)
data class ElectivePackageTrendDto(
    val packageName: String,
    val totalSelections: Int,
    val byYear: List<ElectivePackageYearDto> = emptyList()
)
data class ElectivePackageYearDto(
    val academicYearId: Int,
    val academicYearName: String,
    val packageId: Int,
    val selections: Int
)
data class ElectivePreviewDto(
    val packageId: Int,
    val packageName: String,
    val academicYearId: Int,
    val academicYearName: String,
    val gradeLevel: String,
    val capacity: Int,
    val totalStudents: Int,
    val totalClasses: Int,
    val signature: String,
    val classes: List<ElectivePreviewClassDto> = emptyList()
)
data class ElectivePreviewClassDto(
    val name: String,
    val capacity: Int,
    val students: List<ElectivePreviewStudentDto> = emptyList()
)
data class ElectivePreviewStudentDto(
    val studentId: Int,
    val nis: String?,
    val studentName: String
)
data class ElectiveApplyRequest(
    val packageId: Int,
    val gradeLevel: String,
    val signature: String
)
data class ElectiveAppliedDto(
    val packageId: Int,
    val academicYearId: Int,
    val classes: List<ElectiveCreatedClassDto> = emptyList()
)
data class ElectiveCreatedClassDto(val id: Int, val name: String, val studentCount: Int)

interface ElectiveAllocationApi {
    @GET("admin/elective/package-trends")
    suspend fun trends(): ApiResponse<ElectiveTrendsDto>

    @GET("admin/elective/allocations/preview")
    suspend fun preview(
        @Query("packageId") packageId: Int,
        @Query("gradeLevel") gradeLevel: String
    ): ApiResponse<ElectivePreviewDto>

    @POST("admin/elective/allocations")
    suspend fun apply(@Body request: ElectiveApplyRequest): ApiResponse<ElectiveAppliedDto>
}
