package id.sch.santaagnes.sim.ui.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.AuthApi
import id.sch.santaagnes.sim.data.repository.GradesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import java.util.Locale
import javax.inject.Inject

data class ReportOption(val id: Int, val name: String)

data class ReportAssessmentUi(val title: String, val value: String)
data class ReportComponentUi(
    val name: String,
    val value: String,
    val assessments: List<ReportAssessmentUi> = emptyList()
)

data class ReportEntryUi(
    val title: String,
    val subtitle: String = "",
    val components: List<ReportComponentUi>,
    val finalScore: String,
    val predicate: String,
    val isComplete: Boolean = false
)

data class GradeReportUiState(
    val loading: Boolean = false,
    val role: String = "",
    val options: List<ReportOption> = emptyList(),
    val selectedId: Int? = null,
    val entries: List<ReportEntryUi> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class GradeReportViewModel @Inject constructor(
    private val retrofit: Retrofit,
    private val authApi: AuthApi,
    private val gradesRepository: GradesRepository
) : ViewModel() {
    private val api by lazy { retrofit.create(GradesReportApi::class.java) }
    private val _state = MutableStateFlow(GradeReportUiState())
    val state = _state.asStateFlow()

    fun start(role: String) {
        if (_state.value.role == role && _state.value.options.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = GradeReportUiState(loading = true, role = role)
            try {
                when (role) {
                    "STUDENT" -> {
                        _state.value = _state.value.copy(loading = false)
                        load(0)
                    }
                    "GUARDIAN" -> {
                        val children = authApi.getProfile().data?.guardian?.children.orEmpty()
                        val options = children.map {
                            ReportOption(it.id, "${it.name} (${it.nis.orEmpty()})")
                        }
                        _state.value = _state.value.copy(loading = false, options = options)
                        if (options.isNotEmpty()) load(options.first().id)
                    }
                    "TEACHER" -> {
                        // Wali kelas juga melihat mapel guru lain di kelas binaannya (hanya baca).
                        val options = gradesRepository.assignments(includeHomeroom = true).map {
                            val owner = it.teacherName?.takeIf { _ -> !it.isOwn }
                            ReportOption(it.id, if (owner == null) it.label else "${it.label} — $owner")
                        }
                        _state.value = _state.value.copy(loading = false, options = options)
                        if (options.isNotEmpty()) load(options.first().id)
                    }
                    else -> _state.value = _state.value.copy(
                        loading = false, error = "Role tidak didukung."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun load(id: Int) = viewModelScope.launch {
        val role = _state.value.role
        _state.value = _state.value.copy(loading = true, selectedId = id, error = null)
        try {
            val entries: List<ReportEntryUi> = when (role) {
                "STUDENT" -> api.ownScores().data.orEmpty().map { formatSubject(it) }
                "GUARDIAN" -> api.studentScores(id).data.orEmpty().map { formatSubject(it) }
                "TEACHER" -> api.classReport(id).data.map { row ->
                    ReportEntryUi(
                        title = row.name,
                        subtitle = row.nis.orEmpty(),
                        components = row.components.map { part ->
                            ReportComponentUi(part.name, componentScore(part.value))
                        },
                        finalScore = scoreText(row.finalScore),
                        predicate = predicateText(row.finalScore, row.predicate),
                        isComplete = row.complete
                    )
                }
                else -> emptyList()
            }
            _state.value = _state.value.copy(loading = false, entries = entries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun formatSubject(row: SubjectReportDto): ReportEntryUi = ReportEntryUi(
        title = row.subjectName,
        subtitle = row.className,
        components = row.components.map { part ->
            ReportComponentUi(
                "${part.name} (${part.weight}%)",
                componentScore(part.value),
                part.assessments.sortedBy { it.sequence }.map { assessment ->
                    ReportAssessmentUi(assessment.title, componentScore(assessment.value))
                }
            )
        },
        finalScore = scoreText(row.finalScore),
        predicate = predicateText(row.finalScore, row.predicate),
        isComplete = row.complete
    )

    private fun componentScore(value: Double?): String =
        value?.let { String.format(Locale("id", "ID"), "%.2f", it) } ?: "-"

    private fun scoreText(score: Double?): String =
        score?.let { String.format(Locale("id", "ID"), "%.2f", it) } ?: "Belum lengkap"

    private fun predicateText(score: Double?, predicate: String?): String =
        if (score == null) "Belum lengkap" else predicate ?: "Belum tersedia"

    private fun fail(e: Exception) {
        _state.value = _state.value.copy(
            loading = false,
            error = GradesRepository.friendlyError(e)
        )
    }
}
