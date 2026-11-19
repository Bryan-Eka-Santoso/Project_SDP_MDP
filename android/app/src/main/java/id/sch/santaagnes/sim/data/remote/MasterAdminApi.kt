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

data class MasterImportDto(
    val imported: Int,
    val assignedToClass: Int = 0
)

// =====================================================================
// DTO — MASTER PERIODE AKADEMIK (F-MST-06)
// =====================================================================

data class AcademicYearCountDto(
    val classGroups: Int = 0,
    val semesters: Int = 0
)

data class SemesterCountDto(
    val teachingAssignments: Int = 0
)

data class AcademicYearSummaryDto(
    val id: Int,
    val name: String,
    val isActive: Boolean = false
)

data class MasterSemesterDto(
    val id: Int,
    val academicYearId: Int,
    val type: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean,
    val academicYear: AcademicYearSummaryDto? = null,
    val _count: SemesterCountDto? = null
)

data class MasterAcademicYearDto(
    val id: Int,
    val name: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean,
    val _count: AcademicYearCountDto? = null,
    val activeSemester: MasterSemesterDto? = null,
    val semesters: List<MasterSemesterDto>? = null
)

data class CreateAcademicYearRequest(
    val name: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean = false
)

data class UpdateAcademicYearRequest(
    val name: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

data class CreateSemesterRequest(
    val academicYearId: Int,
    val type: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean = false
)

data class UpdateSemesterRequest(
    val startDate: String? = null,
    val endDate: String? = null
)

data class GradePredicateDto(
    val id: Int = 0,
    val academicYearId: Int = 0,
    val label: String,
    val minScore: Double,
    val description: String? = null
)

data class GradePredicateScaleDto(
    val academicYear: AcademicYearSummaryDto,
    val predicates: List<GradePredicateDto>
)

data class GradePredicateInputDto(
    val label: String,
    val minScore: Double,
    val description: String? = null
)

data class ReplaceGradePredicatesRequest(
    val predicates: List<GradePredicateInputDto>
)

// =====================================================================
// DTO — MASTER GURU & PENUGASAN MENGAJAR (F-MST-05, F-MST-10)
// =====================================================================

data class MasterTeacherHomeroomClassDto(
    val id: Int,
    val name: String,
    val gradeLevel: String,
    val academicYear: AcademicYearSummaryDto? = null
)

data class MasterTeacherAssignmentSummaryDto(
    val id: Int,
    val subject: SubjectDto,
    val classGroup: IdNameDto,
    val semester: MasterSemesterSummaryDto
)

data class MasterSemesterSummaryDto(
    val id: Int,
    val type: String,
    val isActive: Boolean
)

data class MasterTeacherDto(
    val id: Int,
    val employeeNumber: String,
    val isHomeroomTeacher: Boolean = false,
    val user: StudentUserDto,
    val homeroomClasses: List<MasterTeacherHomeroomClassDto>? = null,
    val teachingAssignments: List<MasterTeacherAssignmentSummaryDto>? = null
)

data class CreateTeacherRequest(
    val name: String,
    val email: String,
    val password: String,
    val employeeNumber: String
)

data class UpdateTeacherRequest(
    val name: String? = null,
    val email: String? = null,
    val employeeNumber: String? = null
)

data class MasterAssignmentUserDto(
    val id: Int,
    val name: String,
    val email: String? = null
)

data class MasterAssignmentTeacherDto(
    val id: Int,
    val employeeNumber: String,
    val user: MasterAssignmentUserDto
)

data class MasterAssignmentClassDto(
    val id: Int,
    val name: String,
    val gradeLevel: String? = null,
    val academicYear: AcademicYearSummaryDto? = null
)

data class MasterAssignmentCountDto(
    val scheduleSlots: Int = 0,
    val scores: Int = 0,
    val gradeComponents: Int = 0,
    val tasks: Int = 0
)

data class MasterAssignmentSubjectDto(
    val id: Int,
    val code: String? = null,
    val name: String,
    val type: String? = null
)

data class MasterTeachingAssignmentDto(
    val id: Int,
    val teacher: MasterAssignmentTeacherDto,
    val subject: MasterAssignmentSubjectDto,
    val classGroup: MasterAssignmentClassDto,
    val semester: MasterSemesterSummaryDto,
    val _count: MasterAssignmentCountDto? = null
)

data class CreateTeachingAssignmentRequest(
    val teacherId: Int,
    val subjectId: Int,
    val classGroupId: Int,
    val semesterId: Int
)

data class UpdateTeachingAssignmentRequest(
    val teacherId: Int
)

// =====================================================================
// DTO — MASTER KELAS & ANGGOTA KELAS (F-MST-07, F-MST-08, F-MST-09)
// =====================================================================

data class MasterClassCountDto(
    val members: Int = 0,
    val teachingAssignments: Int = 0
)

data class MasterClassGroupDto(
    val id: Int,
    val name: String,
    val gradeLevel: String,
    val capacity: Int,
    val academicYear: AcademicYearSummaryDto,
    val homeroomTeacher: MasterAssignmentTeacherDto? = null,
    val _count: MasterClassCountDto? = null
)

data class CreateClassRequest(
    val academicYearId: Int,
    val name: String,
    val gradeLevel: String,
    val capacity: Int,
    val homeroomTeacherId: Int? = null
)

data class UpdateClassRequest(
    val name: String? = null,
    val gradeLevel: String? = null,
    val capacity: Int? = null
)

data class SetHomeroomTeacherRequest(
    val homeroomTeacherId: Int?
)

data class ClassGroupShortDto(
    val id: Int,
    val name: String,
    val capacity: Int
)

data class ClassMemberItemDto(
    val studentId: Int,
    val nis: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val birthDate: String? = null
)

data class ClassMembersResponseDto(
    val classGroup: ClassGroupShortDto,
    val total: Int,
    val members: List<ClassMemberItemDto>
)

data class AddClassMembersRequest(
    val studentIds: List<Int>
)

data class MoveClassMemberRequest(
    val studentId: Int,
    val targetClassGroupId: Int
)

// =====================================================================
// DTO — MASTER WALI MURID (F-MST-04)
// =====================================================================

data class GuardianCountDto(
    val students: Int = 0
)

data class MasterGuardianDto(
    val id: Int,
    val phone: String,
    val user: StudentUserDto,
    val _count: GuardianCountDto? = null
)

data class CreateGuardianRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String
)

data class UpdateGuardianRequest(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null
)

// =====================================================================
// RETROFIT INTERFACES
// =====================================================================

interface AcademicYearApi {

    @GET("academic-years/active")
    suspend fun getActiveAcademicYear(): ApiResponse<MasterAcademicYearDto>

    @GET("academic-years")
    suspend fun getAcademicYears(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("q") search: String? = null
    ): ApiResponse<List<MasterAcademicYearDto>>

    @GET("academic-years/{id}")
    suspend fun getAcademicYearById(
        @Path("id") id: Int
    ): ApiResponse<MasterAcademicYearDto>

    @POST("academic-years")
    suspend fun createAcademicYear(
        @Body body: CreateAcademicYearRequest
    ): ApiResponse<MasterAcademicYearDto>

    @PATCH("academic-years/{id}")
    suspend fun updateAcademicYear(
        @Path("id") id: Int,
        @Body body: UpdateAcademicYearRequest
    ): ApiResponse<MasterAcademicYearDto>

    @PATCH("academic-years/{id}/active")
    suspend fun setActiveAcademicYear(
        @Path("id") id: Int
    ): ApiResponse<MasterAcademicYearDto>

    @DELETE("academic-years/{id}")
    suspend fun deleteAcademicYear(
        @Path("id") id: Int
    ): Response<Unit>

    @GET("semesters/active")
    suspend fun getActiveSemester(): ApiResponse<MasterSemesterDto>

    @GET("semesters")
    suspend fun getSemesters(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("academicYearId") academicYearId: Int? = null
    ): ApiResponse<List<MasterSemesterDto>>

    @POST("semesters")
    suspend fun createSemester(
        @Body body: CreateSemesterRequest
    ): ApiResponse<MasterSemesterDto>

    @PATCH("semesters/{id}")
    suspend fun updateSemester(
        @Path("id") id: Int,
        @Body body: UpdateSemesterRequest
    ): ApiResponse<MasterSemesterDto>

    @PATCH("semesters/{id}/active")
    suspend fun setActiveSemester(
        @Path("id") id: Int
    ): ApiResponse<MasterSemesterDto>

    @DELETE("semesters/{id}")
    suspend fun deleteSemester(
        @Path("id") id: Int
    ): Response<Unit>

    @GET("academic-years/{id}/grade-predicates")
    suspend fun getGradePredicates(
        @Path("id") academicYearId: Int
    ): ApiResponse<GradePredicateScaleDto>

    @PATCH("academic-years/{id}/grade-predicates")
    suspend fun replaceGradePredicates(
        @Path("id") academicYearId: Int,
        @Body body: ReplaceGradePredicatesRequest
    ): ApiResponse<GradePredicateScaleDto>
}

interface TeacherApi {

    @GET("teachers")
    suspend fun getTeachers(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("q") search: String? = null,
        @Query("isHomeroomTeacher") isHomeroomTeacher: Boolean? = null
    ): ApiResponse<List<MasterTeacherDto>>

    @GET("teachers/{id}")
    suspend fun getTeacherById(
        @Path("id") id: Int
    ): ApiResponse<MasterTeacherDto>

    @POST("teachers")
    suspend fun createTeacher(
        @Body body: CreateTeacherRequest
    ): ApiResponse<MasterTeacherDto>

    @PATCH("teachers/{id}")
    suspend fun updateTeacher(
        @Path("id") id: Int,
        @Body body: UpdateTeacherRequest
    ): ApiResponse<MasterTeacherDto>

    @DELETE("teachers/{id}")
    suspend fun deleteTeacher(
        @Path("id") id: Int
    ): Response<Unit>

    @Multipart
    @POST("teachers/import")
    suspend fun importTeachers(
        @Part file: MultipartBody.Part
    ): ApiResponse<MasterImportDto>
}

interface TeachingAssignmentApi {

    @GET("teaching-assignments")
    suspend fun getTeachingAssignments(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("semesterId") semesterId: Int? = null,
        @Query("teacherId") teacherId: Int? = null,
        @Query("classGroupId") classGroupId: Int? = null,
        @Query("subjectId") subjectId: Int? = null
    ): ApiResponse<List<MasterTeachingAssignmentDto>>

    @POST("teaching-assignments")
    suspend fun createTeachingAssignment(
        @Body body: CreateTeachingAssignmentRequest
    ): ApiResponse<MasterTeachingAssignmentDto>

    @PATCH("teaching-assignments/{id}")
    suspend fun updateTeachingAssignment(
        @Path("id") id: Int,
        @Body body: UpdateTeachingAssignmentRequest
    ): ApiResponse<MasterTeachingAssignmentDto>

    @DELETE("teaching-assignments/{id}")
    suspend fun deleteTeachingAssignment(
        @Path("id") id: Int
    ): Response<Unit>
}

interface ClassApi {

    @GET("classes")
    suspend fun getClasses(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("academicYearId") academicYearId: Int? = null,
        @Query("gradeLevel") gradeLevel: String? = null,
        @Query("q") search: String? = null
    ): ApiResponse<List<MasterClassGroupDto>>

    @POST("classes")
    suspend fun createClass(
        @Body body: CreateClassRequest
    ): ApiResponse<MasterClassGroupDto>

    @PATCH("classes/{id}")
    suspend fun updateClass(
        @Path("id") id: Int,
        @Body body: UpdateClassRequest
    ): ApiResponse<MasterClassGroupDto>

    @PATCH("classes/{id}/homeroom-teacher")
    suspend fun setHomeroomTeacher(
        @Path("id") id: Int,
        @Body body: SetHomeroomTeacherRequest
    ): ApiResponse<MasterClassGroupDto>

    @DELETE("classes/{id}")
    suspend fun deleteClass(
        @Path("id") id: Int
    ): Response<Unit>

    @GET("classes/{id}/members")
    suspend fun getClassMembers(
        @Path("id") id: Int
    ): ApiResponse<ClassMembersResponseDto>

    @POST("classes/{id}/members")
    suspend fun addClassMembers(
        @Path("id") id: Int,
        @Body body: AddClassMembersRequest
    ): ApiResponse<ClassMembersResponseDto>

    @POST("classes/{id}/members/move")
    suspend fun moveClassMember(
        @Path("id") id: Int,
        @Body body: MoveClassMemberRequest
    ): ApiResponse<Map<String, Any>>

    @DELETE("classes/{id}/members/{studentId}")
    suspend fun removeClassMember(
        @Path("id") id: Int,
        @Path("studentId") studentId: Int
    ): Response<Unit>
}

interface GuardianApi {

    @GET("guardians")
    suspend fun getGuardians(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("q") search: String? = null
    ): ApiResponse<List<MasterGuardianDto>>

    @POST("guardians")
    suspend fun createGuardian(
        @Body body: CreateGuardianRequest
    ): ApiResponse<MasterGuardianDto>

    @PATCH("guardians/{id}")
    suspend fun updateGuardian(
        @Path("id") id: Int,
        @Body body: UpdateGuardianRequest
    ): ApiResponse<MasterGuardianDto>

    @DELETE("guardians/{id}")
    suspend fun deleteGuardian(
        @Path("id") id: Int
    ): Response<Unit>
}
