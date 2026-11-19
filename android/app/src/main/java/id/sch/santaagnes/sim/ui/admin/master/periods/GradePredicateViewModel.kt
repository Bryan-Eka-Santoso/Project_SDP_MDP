package id.sch.santaagnes.sim.ui.admin.master.periods

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.domain.model.GradePredicateItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong

data class GradePredicateUiState(
    val academicYearId: Int = 0,
    val academicYearName: String = "",
    val items: List<GradePredicateItem> = emptyList(),
    val savedSnapshot: List<GradePredicateItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val hasZeroThreshold: Boolean
        get() = items.any { (it.minScore * 100.0).roundToLong() == 0L }

    val hasUnsavedChanges: Boolean
        get() = items.map { Triple(it.label, (it.minScore * 100.0).roundToLong(), it.description) } !=
            savedSnapshot.map { Triple(it.label, (it.minScore * 100.0).roundToLong(), it.description) }

    val canSave: Boolean
        get() = !isLoading && !isSaving && items.isNotEmpty() && hasZeroThreshold
}

/** Mengelola skala predikat nilai per tahun ajaran (F-NIL-06). */
@HiltViewModel
class GradePredicateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AcademicYearRepository
) : ViewModel() {

    private val academicYearId: Int = savedStateHandle.get<Int>(ARG_ACADEMIC_YEAR_ID) ?: 0
    private val initialYearName: String = savedStateHandle.get<String>(ARG_ACADEMIC_YEAR_NAME).orEmpty()

    private val _uiState = MutableStateFlow(
        GradePredicateUiState(
            academicYearId = academicYearId,
            academicYearName = initialYearName
        )
    )
    val uiState: StateFlow<GradePredicateUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        if (academicYearId > 0) {
            load()
        }
    }

    fun load() {
        if (academicYearId <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getGradePredicates(academicYearId)) {
                is Resource.Success -> {
                    val sorted = res.data.predicates.sortedByDescending { it.minScore }
                    _uiState.update {
                        it.copy(
                            academicYearName = res.data.academicYearName,
                            items = sorted,
                            savedSnapshot = sorted,
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    _message.emit(res.message)
                }
            }
        }
    }

    /** Validasi + tambah/ubah satu baris predikat ke daftar kerja; kembalikan pesan error jika invalid. */
    fun upsertItem(
        editingIndex: Int?,
        rawLabel: String,
        rawMinScore: String,
        rawDescription: String
    ): String? {
        val label = rawLabel.trim()
        if (label.isEmpty()) return "Label predikat wajib diisi."
        if (label.length > 5) return "Label predikat maksimal 5 karakter."

        val minScore = rawMinScore.trim().replace(',', '.').toDoubleOrNull()
            ?: return "Nilai minimum harus berupa angka."
        if (!minScore.isFinite() || minScore < 0.0 || minScore > 100.0) {
            return "Nilai minimum harus di antara 0 dan 100."
        }
        val cents = (minScore * 100.0).roundToLong()
        if (abs(cents - minScore * 100.0) >= 1e-6) {
            return "Nilai minimum maksimal dua angka desimal."
        }
        val normalizedScore = cents / 100.0

        val current = _uiState.value.items
        if (editingIndex == null && current.size >= MAX_PREDICATES) {
            return "Maksimal $MAX_PREDICATES baris predikat per tahun ajaran."
        }

        val duplicateLabel = current.withIndex().any { (idx, item) ->
            idx != editingIndex && item.label.equals(label, ignoreCase = true)
        }
        if (duplicateLabel) return "Label predikat \"$label\" sudah ada."

        val duplicateScore = current.withIndex().any { (idx, item) ->
            idx != editingIndex && (item.minScore * 100.0).roundToLong() == cents
        }
        if (duplicateScore) return "Nilai minimum ${formatScore(normalizedScore)} sudah dipakai."

        val description = rawDescription.trim().takeIf { it.isNotEmpty() }
        if (description != null && description.length > 50) {
            return "Keterangan maksimal 50 karakter."
        }

        val newItem = GradePredicateItem(
            id = editingIndex?.let { current.getOrNull(it)?.id } ?: 0,
            label = label,
            minScore = normalizedScore,
            description = description
        )

        val updated = current.toMutableList().apply {
            if (editingIndex != null && editingIndex in indices) {
                set(editingIndex, newItem)
            } else {
                add(newItem)
            }
        }.sortedByDescending { it.minScore }

        _uiState.update { it.copy(items = updated) }
        return null
    }

    fun removeItem(index: Int) {
        val current = _uiState.value.items
        if (index !in current.indices) return
        val updated = current.toMutableList().apply { removeAt(index) }
        _uiState.update { it.copy(items = updated) }
    }

    fun applyDefaultScale() {
        val defaults = listOf(
            GradePredicateItem(label = "A", minScore = 90.0, description = "Sangat Baik"),
            GradePredicateItem(label = "B", minScore = 80.0, description = "Baik"),
            GradePredicateItem(label = "C", minScore = 70.0, description = "Cukup"),
            GradePredicateItem(label = "D", minScore = 0.0, description = "Perlu Bimbingan")
        )
        _uiState.update { it.copy(items = defaults) }
    }

    fun saveScale() {
        val state = _uiState.value
        if (academicYearId <= 0 || state.isSaving) return
        if (state.items.isEmpty()) {
            _message.tryEmit("Minimal harus ada satu baris skala predikat.")
            return
        }
        if (!state.hasZeroThreshold) {
            _message.tryEmit("Skala wajib memiliki satu baris dengan nilai minimum 0.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            when (val res = repository.replaceGradePredicates(academicYearId, state.items)) {
                is Resource.Success -> {
                    val sorted = res.data.predicates.sortedByDescending { it.minScore }
                    _uiState.update {
                        it.copy(
                            academicYearName = res.data.academicYearName,
                            items = sorted,
                            savedSnapshot = sorted,
                            isSaving = false
                        )
                    }
                    _message.emit("Skala predikat nilai berhasil disimpan")
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSaving = false, errorMessage = res.message) }
                    _message.emit(res.message)
                }
            }
        }
    }

    companion object {
        const val ARG_ACADEMIC_YEAR_ID = "academicYearId"
        const val ARG_ACADEMIC_YEAR_NAME = "academicYearName"
        private const val MAX_PREDICATES = 20

        fun formatScore(score: Double): String {
            val roundedInt = score.roundToLong()
            return if (abs(score - roundedInt) < 1e-6) {
                roundedInt.toString()
            } else {
                score.toBigDecimal().stripTrailingZeros().toPlainString()
            }
        }
    }
}
