package id.sch.santaagnes.sim.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class SurveiSiswaDto(
    val id: Int,
    val title: String,
    val academicYearName: String,
    val gradeLevel: String,
    val openDate: String,
    val closeDate: String,
    val status: String,
    val canFill: Boolean,
    val hasSubmitted: Boolean,
    val selectedSubjectIds: List<Int>
)

data class PilihanMapelDto(
    val id: Int,
    val code: String,
    val name: String
)

data class DetailSurveiSiswaDto(
    val survey: SurveiSiswaDto,
    val subjects: List<PilihanMapelDto>
)

data class JawabanSurveiRequest(
    val subjectIds: List<Int>
)

data class HasilJawabanSurveiDto(
    val surveyId: Int,
    val studentId: Int,
    val selectedSubjectIds: List<Int>,
    val filledDate: String,
    val message: String
)

interface IsiSurveiMinatApi {

    @GET("student-elective-surveys")
    suspend fun getSurveys():
            ApiResponse<List<SurveiSiswaDto>>

    @GET("student-elective-surveys/{id}")
    suspend fun getDetail(
        @Path("id") id: Int
    ): ApiResponse<DetailSurveiSiswaDto>

    @POST("student-elective-surveys/{id}/responses")
    suspend fun submit(
        @Path("id") id: Int,
        @Body body: JawabanSurveiRequest
    ): ApiResponse<HasilJawabanSurveiDto>
}
