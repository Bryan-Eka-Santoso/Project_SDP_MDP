
package id.sch.santaagnes.sim.ui.grades

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GradeComponentViewModelTest {

    private lateinit var viewModel: GradeComponentViewModel

    @Before
    fun setup() {
        viewModel = GradeComponentViewModel()
    }

    @Test
    fun totalBobotHarus100Persen() {
        viewModel.saveComponent("Tugas", "30")
        viewModel.saveComponent("UTS", "30")
        viewModel.saveComponent("UAS", "40")

        val state = viewModel.uiState.value

        assertEquals(3, state.components.size)
        assertEquals(100, state.totalWeight)
        assertTrue(state.isValid)
    }

    @Test
    fun totalBobotTidakBolehLebih100Persen() {
        viewModel.saveComponent("Tugas", "60")
        viewModel.saveComponent("UTS", "50")

        val state = viewModel.uiState.value

        assertEquals(1, state.components.size)
        assertEquals(60, state.totalWeight)
        assertFalse(state.isValid)
    }

    @Test
    fun namaKomponenTidakBolehDuplikat() {
        viewModel.saveComponent("Tugas", "30")
        viewModel.saveComponent("tugas", "20")

        val state = viewModel.uiState.value

        assertEquals(1, state.components.size)
        assertEquals(30, state.totalWeight)
    }

    @Test
    fun bobotBisaDiedit() {
        viewModel.saveComponent("Tugas", "30")

        val componentId =
            viewModel.uiState.value.components.first().id

        viewModel.selectComponent(componentId)
        viewModel.saveComponent("Tugas", "40")

        val state = viewModel.uiState.value

        assertEquals(1, state.components.size)
        assertEquals(40, state.totalWeight)
        assertNull(state.selectedId)
    }

    @Test
    fun komponenBisaDihapus() {
        viewModel.saveComponent("Tugas", "30")
        viewModel.saveComponent("UTS", "70")

        val componentId =
            viewModel.uiState.value.components.first().id

        viewModel.deleteComponent(componentId)

        val state = viewModel.uiState.value

        assertEquals(1, state.components.size)
        assertEquals(70, state.totalWeight)
        assertFalse(state.isValid)
    }
}
