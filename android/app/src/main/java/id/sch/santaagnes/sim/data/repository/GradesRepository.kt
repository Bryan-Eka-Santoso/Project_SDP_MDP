package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.data.remote.GradeAssessmentDto
import id.sch.santaagnes.sim.data.remote.GradeAssessmentRequest
import id.sch.santaagnes.sim.data.remote.GradeAssessmentScoreRequest
import id.sch.santaagnes.sim.data.remote.GradeComponentRequest
import id.sch.santaagnes.sim.data.remote.GradeScoreRequest
import id.sch.santaagnes.sim.data.remote.GradesApi
import id.sch.santaagnes.sim.domain.model.GradeAssignment
import id.sch.santaagnes.sim.domain.model.GradeComponent
import id.sch.santaagnes.sim.domain.model.GradeSchedule
import id.sch.santaagnes.sim.domain.model.GradeScore
import id.sch.santaagnes.sim.domain.model.GradeStudent
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class GradesRepository @Inject constructor(private val api: GradesApi) {
    suspend fun assignments(): List<GradeAssignment> =
        api.assignments().data.orEmpty().map { dto ->
            GradeAssignment(
                id = dto.id,
                label = dto.label,
                subjectName = dto.subjectName ?: dto.label.substringBefore(" - "),
                className = dto.className ?: "",
                semesterName = dto.semesterName ?: "",
                schedules = dto.schedules.map { slot ->
                    GradeSchedule(slot.id, slot.day, slot.startTime, slot.endTime)
                }
            )
        }

    suspend fun students(assignmentId: Int): List<GradeStudent> =
        api.students(assignmentId).data.orEmpty().map { GradeStudent(it.id, it.name, it.nis) }

    suspend fun components(assignmentId: Int): List<GradeComponent> =
        api.components(assignmentId).data.orEmpty().map { GradeComponent(it.id, it.name, it.weight) }

    suspend fun scores(assignmentId: Int): List<GradeScore> =
        api.scores(assignmentId).data.map { GradeScore(it.studentId, it.gradeComponentId, it.value) }

    suspend fun saveComponent(assignmentId: Int, currentId: Int?, name: String, weight: Int) {
        val body = GradeComponentRequest(name, weight)
        if (currentId == null) api.createComponent(assignmentId, body)
        else api.updateComponent(currentId, body)
    }

    suspend fun assessments(assignmentId: Int): List<GradeAssessmentDto> =
        api.assessments(assignmentId).data.orEmpty()

    suspend fun createAssessment(assignmentId: Int, componentId: Int, title: String?) {
        api.createAssessment(assignmentId, GradeAssessmentRequest(componentId, title))
    }

    suspend fun saveAssessmentScore(assessmentId: Int, studentId: Int, value: Double) {
        api.saveAssessmentScore(assessmentId, GradeAssessmentScoreRequest(studentId, value))
    }

    suspend fun saveScore(assignmentId: Int, studentId: Int, componentId: Int, score: Double) {
        api.saveScore(GradeScoreRequest(assignmentId, componentId, studentId, score))
    }

    companion object {
        fun friendlyError(error: Throwable): String = when (error) {
            is IOException -> "Tidak dapat terhubung ke server. Periksa koneksi internet Anda."
            is HttpException -> when (error.code()) {
                400 -> "Data yang diisi tidak valid."
                401 -> "Sesi login Anda telah berakhir. Silakan masuk kembali."
                403 -> "Anda tidak memiliki akses ke data ini."
                404 -> "Data tidak ditemukan atau bukan kelas yang Anda ajar."
                409 -> "Bobot komponen harus berjumlah 100% sebelum input nilai."
                else -> "Terjadi kesalahan pada server. Silakan coba lagi."
            }
            else -> error.message ?: "Terjadi kesalahan."
        }
    }
}
