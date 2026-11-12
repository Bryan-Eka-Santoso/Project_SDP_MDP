
package id.sch.santaagnes.sim.ui.grades

import androidx.lifecycle.ViewModel
import id.sch.santaagnes.sim.domain.model.GradeComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GradeComponentViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        GradeComponentUiState()
    )

    val uiState = _uiState.asStateFlow()

    private var nextId = 1

    fun saveComponent(name: String, weightText: String) {
        val current = _uiState.value
        val cleanName = name.trim()
        val weight = weightText.toIntOrNull()

        if (cleanName.isBlank() || weight == null) {
            showMessage("Nama dan bobot wajib diisi dengan benar")
            return
        }

        if (cleanName.length > 50) {
            showMessage("Nama komponen maksimal 50 karakter")
            return
        }

        if (weight !in 1..100) {
            showMessage("Bobot harus antara 1 sampai 100 persen")
            return
        }

        val duplicate = current.components.any {
            it.name.equals(cleanName, ignoreCase = true) &&
                    it.id != current.selectedId
        }

        if (duplicate) {
            showMessage("Nama komponen sudah digunakan")
            return
        }

        val otherComponents = current.components.filter {
            it.id != current.selectedId
        }

        val newTotal = otherComponents.sumOf { it.weight } + weight

        if (newTotal > 100) {
            showMessage("Total bobot tidak boleh melebihi 100%")
            return
        }

        val component = GradeComponent(
            id = current.selectedId ?: nextId++,
            name = cleanName,
            weight = weight
        )

        updateComponents(otherComponents + component)
    }

    fun selectComponent(id: Int) {
        val component = _uiState.value.components.find {
            it.id == id
        } ?: return

        _uiState.value = _uiState.value.copy(
            selectedId = component.id
        )
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(
            selectedId = null
        )
    }

    fun deleteComponent(id: Int) {
        val updated = _uiState.value.components.filter {
            it.id != id
        }
        updateComponents(updated)
    }

    private fun updateComponents(
        components: List<GradeComponent>
    ) {
        val total = components.sumOf { it.weight }

        _uiState.value = _uiState.value.copy(
            components = components.sortedBy { it.id },
            selectedId = null,
            totalWeight = total,
            isValid = components.isNotEmpty() && total == 100,
            message = null
        )
    }

    private fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(
            message = message
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            message = null
        )
    }
}
