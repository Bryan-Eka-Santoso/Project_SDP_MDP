package id.sch.santaagnes.sim.ui.grades

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.repository.GradesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GradeManageViewModel @Inject constructor(private val repository: GradesRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GradeManageUiState())
    val uiState = _uiState.asStateFlow()

    fun load(id: Int) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, selectedAssignmentId = id, errorMessage = null, message = null)
        try {
            val students = repository.students(id)
            val components = repository.components(id)
            val scores = repository.scores(id)
            val assessments = repository.assessments(id)
            _uiState.value = _uiState.value.copy(isLoading = false, students = students, components = components, scores = scores, assessments = assessments)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { showError(e) }
    }

    fun createAssessment(componentId: Int, title: String) {
        val id = _uiState.value.selectedAssignmentId ?: return
        if (_uiState.value.isLoading) return
        if (title.isBlank() || title.length > 80) {
            _uiState.value = _uiState.value.copy(errorMessage = "Nama penilaian wajib diisi (maks. 80 karakter).")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, message = null)
        viewModelScope.launch {
            try {
                repository.createAssessment(id, componentId, title.trim())
                val assessments = repository.assessments(id)
                _uiState.value = _uiState.value.copy(isLoading = false, assessments = assessments, message = "Penilaian berhasil ditambahkan.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
        }
    }

    fun saveAssessmentScore(assessmentId: Int, studentId: Int, input: String) {
        val id = _uiState.value.selectedAssignmentId ?: return
        if (_uiState.value.isLoading) return
        if (_uiState.value.totalWeight != 100) {
            _uiState.value = _uiState.value.copy(errorMessage = MESSAGE_WEIGHT_NOT_100)
            return
        }
        val value = input.replace(',', '.').toDoubleOrNull()
        if (value == null || value !in 0.0..100.0 || kotlin.math.abs(value * 100 - kotlin.math.round(value * 100)) > 0.00001) {
            _uiState.value = _uiState.value.copy(errorMessage = "Nilai harus 0–100 (maksimal dua desimal).")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, message = null)
        viewModelScope.launch {
            try {
                repository.saveAssessmentScore(assessmentId, studentId, value)
                val assessments = repository.assessments(id)
                val scores = repository.scores(id)
                _uiState.value = _uiState.value.copy(isLoading = false, assessments = assessments, scores = scores, message = "Detail nilai berhasil disimpan.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
        }
    }

    fun saveComponent(componentId: Int?, name: String, weightText: String) {
        val id = _uiState.value.selectedAssignmentId ?: return
        if (_uiState.value.isLoading) return
        val weight = weightText.toIntOrNull()
        if (name.isBlank() || weight == null || weight !in 1..100) {
            _uiState.value = _uiState.value.copy(errorMessage = "Nama dan bobot 1–100 wajib diisi.")
            return
        }
        val components = _uiState.value.components
        val standardNames = setOf("tugas", "ulangan", "uts", "uas")
        val cleaned = name.trim()
        if (componentId == null && components.size >= 5) {
            _uiState.value = _uiState.value.copy(errorMessage = "Maksimal satu komponen tambahan (Lainnya). Ubah komponen yang sudah ada.")
            return
        }
        if (componentId == null && (cleaned.lowercase() in standardNames || components.any { it.name.equals(cleaned, ignoreCase = true) })) {
            _uiState.value = _uiState.value.copy(errorMessage = "Nama komponen sudah digunakan atau merupakan komponen default.")
            return
        }
        val existing = components.find { it.id == componentId }
        if (existing != null && existing.name.lowercase() in standardNames && !existing.name.equals(cleaned, ignoreCase = true)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Nama komponen default tidak dapat diubah.")
            return
        }
        if (existing != null && components.any { it.id != componentId && it.name.equals(cleaned, ignoreCase = true) }) {
            _uiState.value = _uiState.value.copy(errorMessage = "Nama komponen sudah digunakan.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, message = null)
        viewModelScope.launch {
            try {
                repository.saveComponent(id, componentId, cleaned, weight)
                val components = repository.components(id)
                _uiState.value = _uiState.value.copy(isLoading = false, components = components, message = "Komponen berhasil disimpan.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
        }
    }

    fun saveScores(studentId: Int, values: Map<Int, String>) {
        val id = _uiState.value.selectedAssignmentId ?: return
        if (_uiState.value.isLoading) return
        if (_uiState.value.totalWeight != 100) {
            _uiState.value = _uiState.value.copy(errorMessage = MESSAGE_WEIGHT_NOT_100)
            return
        }
        // Tugas/Ulangan disimpan via detail penilaian, bukan melalui endpoint score langsung.
        val detailedComponents = _uiState.value.assessments.map { it.gradeComponentId }.toSet()
        val nonblank = values.filter { (componentId, input) -> input.isNotBlank() && componentId !in detailedComponents }
        if (nonblank.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Untuk Tugas/Ulangan gunakan tombol Isi Nilai pada setiap penilaian.")
            return
        }
        val parsed = mutableMapOf<Int, Double>()
        for ((componentId, input) in nonblank) {
            val value = input.replace(',', '.').toDoubleOrNull()
            if (value == null || value !in 0.0..100.0 || kotlin.math.abs(value * 100 - kotlin.math.round(value * 100)) > 0.00001) {
                _uiState.value = _uiState.value.copy(errorMessage = "Nilai harus 0–100, maksimal dua angka desimal.")
                return
            }
            parsed[componentId] = value
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, message = null)
        viewModelScope.launch {
            try {
                // Setiap komponen disimpan melalui endpoint upsert yang sama.
                for ((componentId, value) in parsed) repository.saveScore(id, studentId, componentId, value)
                val scores = repository.scores(id)
                _uiState.value = _uiState.value.copy(isLoading = false, scores = scores, message = "Nilai berhasil disimpan.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
        }
    }

    private companion object {
        const val MESSAGE_WEIGHT_NOT_100 = "Total bobot harus 100% untuk menyimpan nilai."
    }

    private fun showError(e: Exception) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = GradesRepository.friendlyError(e))
    }
}
