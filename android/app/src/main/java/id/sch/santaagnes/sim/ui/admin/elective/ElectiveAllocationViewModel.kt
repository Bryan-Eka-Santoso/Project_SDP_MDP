package id.sch.santaagnes.sim.ui.admin.elective

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.ElectiveAppliedDto
import id.sch.santaagnes.sim.data.remote.ElectivePackageYearDto
import id.sch.santaagnes.sim.data.remote.ElectivePreviewDto
import id.sch.santaagnes.sim.data.repository.ElectiveAllocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

data class ElectiveOption(val packageName: String, val yearName: String, val packageId: Int, val selections: Int) {
    val label get() = "$packageName • $yearName ($selections pemilih)"
}

data class ElectiveAllocationState(
    val options: List<ElectiveOption> = emptyList(),
    val selected: ElectiveOption? = null,
    val gradeLevel: String = "X",
    val preview: ElectivePreviewDto? = null,
    val applied: ElectiveAppliedDto? = null,
    val loading: Boolean = false,
    val applying: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class ElectiveAllocationViewModel @Inject constructor(
    private val repository: ElectiveAllocationRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(ElectiveAllocationState())
    val state = mutable.asStateFlow()

    init { loadPackages() }

    fun loadPackages() {
        if (mutable.value.applying) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null, preview = null, applied = null)
            try {
                val response = repository.trends()
                val options = response.packages.flatMap { pkg ->
                    pkg.byYear.map { year: ElectivePackageYearDto ->
                        ElectiveOption(pkg.packageName, year.academicYearName, year.packageId, year.selections)
                    }
                }.sortedWith(compareBy<ElectiveOption> { it.packageName }.thenByDescending { it.yearName })
                mutable.value = mutable.value.copy(options = options, selected = options.firstOrNull(), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    fun selectPackage(index: Int) {
        val current = mutable.value
        val option = current.options.getOrNull(index) ?: return
        if (option.packageId != current.selected?.packageId) {
            mutable.value = current.copy(selected = option, preview = null, applied = null, error = null, message = null)
        }
    }

    fun setGrade(grade: String) {
        val value = grade.trim()
        if (mutable.value.gradeLevel != value) {
            mutable.value = mutable.value.copy(gradeLevel = value, preview = null, applied = null, message = null)
        }
    }

    fun loadPreview() {
        val old = mutable.value
        val option = old.selected ?: run {
            mutable.value = old.copy(error = "Belum ada paket pilihan yang tersedia.")
            return
        }
        if (old.loading || old.applying) return
        if (old.gradeLevel.isBlank()) {
            mutable.value = old.copy(error = "Tingkat kelas wajib diisi (contoh: X, XI, XII).")
            return
        }
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, preview = null, applied = null, error = null, message = null)
            try {
                val plan = repository.preview(option.packageId, old.gradeLevel)
                // Jika pilihan berubah saat request berjalan, jangan tampilkan preview lama.
                if (mutable.value.selected?.packageId == option.packageId && mutable.value.gradeLevel == old.gradeLevel) {
                    mutable.value = mutable.value.copy(preview = plan, loading = false)
                } else mutable.value = mutable.value.copy(loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    fun applyPreview() {
        val current = mutable.value
        val plan = current.preview ?: return
        if (current.loading || current.applying || plan.totalStudents == 0 || plan.totalClasses == 0) return
        if (current.selected?.packageId != plan.packageId || current.gradeLevel != plan.gradeLevel) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(applying = true, error = null, message = null)
            try {
                val result = repository.apply(plan)
                mutable.value = mutable.value.copy(
                    applying = false, preview = null, applied = result,
                    message = "Berhasil membuat ${result.classes.size} kelas. Periksa data kelas sebelum mengalokasikan ulang."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(applying = false, preview = null, error = friendly(e) + " Muat ulang pratinjau sebelum mencoba lagi.")
            }
        }
    }

    fun clearMessage() { mutable.value = mutable.value.copy(message = null) }

    private fun friendly(e: Exception): String = when (e) {
        is IOException -> "Tidak dapat terhubung ke server. Periksa backend dan koneksi emulator."
        is HttpException -> when (e.code()) {
            401 -> "Sesi login berakhir. Silakan login ulang."
            403 -> "Fitur alokasi hanya dapat digunakan oleh Admin."
            404 -> "Paket mata pelajaran tidak ditemukan."
            409 -> "Alokasi tidak dapat dilanjutkan: ${e.message()}. Paket mungkin sudah dialokasikan atau data berubah."
            else -> "Permintaan gagal (HTTP ${e.code()})."
        }
        else -> e.message ?: "Terjadi kesalahan."
    }
}
