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
class TeacherClassesViewModel @Inject constructor(
    private val repository: GradesRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TeacherClassesUiState())
    val uiState = _uiState.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = TeacherClassesUiState(loading = true)
        try {
            _uiState.value = TeacherClassesUiState(assignments = repository.assignments())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _uiState.value = TeacherClassesUiState(error = GradesRepository.friendlyError(e)) }
    }
}
