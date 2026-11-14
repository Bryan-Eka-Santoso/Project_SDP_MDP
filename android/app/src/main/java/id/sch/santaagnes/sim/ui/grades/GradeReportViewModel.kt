
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

data class ReportOption(
    val id: Int,
    val name: String
)

data class GradeReportUiState(
    val loading: Boolean = false,
    val role: String = "",
    val options: List<ReportOption> = emptyList(),
    val selectedId: Int? = null,
    val entries: List<String> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class GradeReportViewModel @Inject constructor(
    private val retrofit: Retrofit,
    private val authApi: AuthApi,
    private val gradesRepository: GradesRepository
) : ViewModel() {

    private val api by lazy {
        retrofit.create(GradesReportApi::class.java)
    }

    private val _state = MutableStateFlow(GradeReportUiState())
    val state = _state.asStateFlow()

    fun start(role: String) {
        if (
            _state.value.role == role &&
            _state.value.options.isNotEmpty()
        ) return

        viewModelScope.launch {
            _state.value = GradeReportUiState(
                loading = true,
                role = role
            )

            try {
                when (role) {

                    "STUDENT" -> {
                        _state.value = _state.value.copy(

                            loading = false
                        )
                        load(0)
                    }

                    "GUARDIAN" -> {
                        val children = authApi.getProfile()
                            .data
                            ?.guardian
                            ?.children
                            .orEmpty()

                        val options = children.map {
                            ReportOption(
                                id = it.id,
                                name = "${it.name} (${it.nis.orEmpty()})"
                            )
                        }

                        _state.value = _state.value.copy(
                            loading = false,
                            options = options
                        )

                        if (options.isNotEmpty()) {
                            load(options.first().id)
                        }
                    }

                    "TEACHER" -> {
                        val options = gradesRepository.assignments()
                            .map {
                                ReportOption(
                                    id = it.id,
                                    name = it.label
                                )
                            }

                        _state.value = _state.value.copy(
                            loading = false,
                            options = options
                        )

                        if (options.isNotEmpty()) {
                            load(options.first().id)
                        }
                    }

                    else -> {
                        _state.value = _state.value.copy(
                            loading = false,
                            error = "Role tidak didukung."
                        )
                    }
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

        _state.value = _state.value.copy(
            loading = true,
            selectedId = id,
            error = null
        )

        try {
            val entries: List<String> = when (role) {

                "STUDENT" -> {
                    api.ownScores()
                        .data
                        .orEmpty()
                        .map { row ->
                            formatSubject(row)
                        }
                }

                "GUARDIAN" -> {
                    api.studentScores(id)
                        .data
                        .orEmpty()
                        .map { row ->
                            formatSubject(row)
                        }
                }

                "TEACHER" -> {
                    api.classReport(id)
                        .data
                        .map { row ->

                            // Gunakan separator ASCII agar tidak rusak
                            val scores = row.components.joinToString(" | ") { part ->
                                "${part.name}: ${componentScoreText(part.value)}"
                            }

                            "${row.name} (${row.nis.orEmpty()})\n" +
                                    "$scores\n" +
                                    "Nilai akhir: ${scoreText(row.finalScore)}\n" +
                                    "Predikat: ${predicateText(row.finalScore, row.predicate)}"
                        }
                }

                else -> emptyList()
            }

            _state.value = _state.value.copy(
                loading = false,
                entries = entries
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    // Format nilai untuk Siswa dan Wali Murid
    private fun formatSubject(row: SubjectReportDto): String {

        val scores = row.components.joinToString(" | ") { part ->
            "${part.name} (${part.weight}%): ${componentScoreText(part.value)}"
        }

        return "${row.subjectName} - ${row.className}\n" +
                "$scores\n" +
                "Nilai akhir: ${scoreText(row.finalScore)}\n" +
                "Predikat: ${predicateText(row.finalScore, row.predicate)}"
    }

    // Nilai per komponen
    private fun componentScoreText(score: Double?): String {
        return score?.let {
            String.format(Locale.US, "%.2f", it)
        } ?: "-"
    }

    // Nilai akhir
    private fun scoreText(score: Double?): String {
        return score?.let {
            String.format(Locale.US, "%.2f", it)
        } ?: "Belum lengkap"
    }

    // Predikat tetap berasal dari API
    private fun predicateText(
        score: Double?,
        predicate: String?
    ): String {
        return if (score == null) {
            "Belum lengkap"
        } else {
            predicate ?: "Belum tersedia"
        }
    }

    private fun fail(e: Exception) {
        _state.value = _state.value.copy(
            loading = false,
            error = GradesRepository.friendlyError(e)
        )
    }
}
