package id.sch.santaagnes.sim.ui.grades

import id.sch.santaagnes.sim.data.remote.*
import id.sch.santaagnes.sim.data.repository.GradesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradeManageViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun doubleTapOnAddAssessmentSendsOneRequest() = runTest(dispatcher) {
        val api = FakeGradesApi()
        val viewModel = loaded(api)
        advanceUntilIdle()

        viewModel.createAssessment(1, "Tugas 1")
        viewModel.createAssessment(1, "Tugas 1")
        advanceUntilIdle()
        api.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, api.createdAssessments)
    }

    @Test
    fun assessmentScoreIsBlockedWhileTotalWeightIsNot100() = runTest(dispatcher) {
        val api = FakeGradesApi(weights = listOf(30, 30))
        val viewModel = loaded(api)
        advanceUntilIdle()
        api.gate.complete(Unit)

        viewModel.saveAssessmentScore(assessmentId = 7, studentId = 1, input = "80")
        advanceUntilIdle()

        assertEquals(0, api.savedAssessmentScores)
        assertEquals("Total bobot harus 100% untuk menyimpan nilai.", viewModel.uiState.value.errorMessage)
    }

    private fun loaded(api: FakeGradesApi) = GradeManageViewModel(GradesRepository(api)).also { it.load(5) }

    private class FakeGradesApi(private val weights: List<Int> = listOf(60, 40)) : GradesApi {
        val gate = CompletableDeferred<Unit>()
        var createdAssessments = 0
        var savedAssessmentScores = 0

        override suspend fun assignments(includeHomeroom: Boolean?) = ApiResponse(true, emptyList<GradeAssignmentDto>())

        override suspend fun students(assignmentId: Int) = ApiResponse(true, emptyList<GradeStudentDto>())

        override suspend fun components(assignmentId: Int) = ApiResponse(
            true,
            weights.mapIndexed { i, weight -> GradeComponentDto(i + 1, "Komponen ${i + 1}", weight, assignmentId) }
        )

        override suspend fun assessments(assignmentId: Int) = ApiResponse(true, emptyList<GradeAssessmentDto>())

        override suspend fun createAssessment(
            assignmentId: Int,
            body: GradeAssessmentRequest
        ): ApiResponse<GradeAssessmentDto> {
            createdAssessments++
            gate.await()
            return ApiResponse(true, null)
        }

        override suspend fun saveAssessmentScore(
            id: Int,
            body: GradeAssessmentScoreRequest
        ): ApiResponse<GradeAssessmentScoreDto> {
            savedAssessmentScores++
            return ApiResponse(true, null)
        }

        override suspend fun scores(assignmentId: Int, page: Int, limit: Int) =
            GradePageResponse(true, emptyList<GradeScoreDto>(), GradePaginationDto(1, 100, 0, 0))

        override suspend fun createComponent(
            assignmentId: Int,
            body: GradeComponentRequest
        ): ApiResponse<GradeComponentDto> = ApiResponse(true, null)

        override suspend fun updateComponent(id: Int, body: GradeComponentRequest): ApiResponse<GradeComponentDto> =
            ApiResponse(true, null)

        override suspend fun saveScore(body: GradeScoreRequest): ApiResponse<GradeScoreDto> = ApiResponse(true, null)
    }
}
