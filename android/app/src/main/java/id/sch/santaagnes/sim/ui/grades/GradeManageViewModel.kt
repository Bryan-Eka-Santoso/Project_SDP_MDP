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
            _uiState.value = _uiState.value.copy(isLoading = false, students = students, components = components, scores = scores)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { showError(e) }
    }

    fun saveComponent(componentId: Int?, name: String, weightText: String) {
        val id = _uiState.value.selectedAssignmentId ?: return
        val weight = weightText.toIntOrNull()
        if (name.isBlank() || weight == null || weight !in 1..100) {
            _uiState.value = _uiState.value.copy(errorMessage = "Nama dan bobot 1â€“100 wajib diisi.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, message = null)
            try {
                repository.saveComponent(id, componentId, name.trim(), weight)
                val components = repository.components(id)
                _uiState.value = _uiState.value.copy(isLoading = false, components = components, message = "Komponen berhasil disimpan.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
        }
    }

    fun saveScores(studentId: Int, values: Map<Int, String>) {
        val id = _uiState.value.selectedAssignmentId ?: return
        if (_uiState.value.totalWeight != 100) {
            _uiState.value = _uiState.value.copy(errorMessage = "Total bobot harus 100% untuk menyimpan nilai.")
            return
        }
        val nonblank = values.filterValues { it.isNotBlank() }
        if (nonblank.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Isi setidaknya satu nilai.")
            return
        }
        val parsed = mutableMapOf<Int, Double>()
        for ((componentId, input) in nonblank) {
            val value = input.replace(',', '.').toDoubleOrNull()
            if (value == null || value !in 0.0..100.0 || kotlin.math.abs(value * 100 - kotlin.math.round(value * 100)) > 0.00001) {
                _uiState.value = _uiState.value.copy(errorMessage = "Nilai harus 0â€“100, maksimal dua angka desimal.")
                return
            }
            parsed[componentId] = value
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, message = null)
            try {
                // Setiap komponen disimpan melalui endpoint upsert yang sama.
                for ((componentId, value) in parsed) repository.saveScore(id, studentId, componentId, value)
                val scores = repository.scores(id)
                _uiState.value = _uiState.value.copy(isLoading = false, scores = scores, message = "Nilai berhasil disimpan.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError(e) }
        }
    }

    private fun showError(e: Exception) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = GradesRepository.friendlyError(e))
    }
}
