package id.sch.santaagnes.sim.ui.admin.elective

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.ElectiveTrendsDto
import id.sch.santaagnes.sim.data.repository.ElectiveAllocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

data class ElectiveTrendsState(
    val loading: Boolean = false,
    val result: ElectiveTrendsDto? = null,
    val error: String? = null
)

@HiltViewModel
class ElectiveTrendsViewModel @Inject constructor(
    private val repository: ElectiveAllocationRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(ElectiveTrendsState())
    val state = mutable.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (mutable.value.loading) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                mutable.value = ElectiveTrendsState(result = repository.trends())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is IOException -> "Tidak dapat terhubung ke server. Periksa backend dan koneksi emulator."
                    is HttpException -> when (e.code()) {
                        401 -> "Sesi login sudah berakhir. Silakan login ulang."
                        403 -> "Hanya Admin yang dapat melihat tren paket."
                        else -> "Gagal memuat tren (HTTP ${e.code()})."
                    }
                    else -> e.message ?: "Tidak dapat mengambil data tren paket."
                }
                mutable.value = mutable.value.copy(loading = false, error = message)
            }
        }
    }
}
