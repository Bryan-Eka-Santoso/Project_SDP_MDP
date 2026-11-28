package id.sch.santaagnes.sim.ui.admin.journal

import id.sch.santaagnes.sim.core.serverMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.remote.AdminJournalDto
import id.sch.santaagnes.sim.data.repository.AdminJournalRepository
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.TeacherOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

data class AdminJournalState(
    val items: List<AdminJournalDto> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val classGroupId: Int? = null,
    val teacherId: Int? = null,
    val classes: List<NamedId> = emptyList(),
    val teachers: List<TeacherOption> = emptyList()
) {
    val canLoadMore get() = !loading && page > 0 && page < totalPages
    val hasActiveFilter get() = startDate != null || endDate != null || classGroupId != null || teacherId != null
}

@HiltViewModel
class AdminJournalViewModel @Inject constructor(
    private val repository: AdminJournalRepository,
    private val reportRepository: ReportRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(AdminJournalState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    init {
        loadOptions()
        refresh()
    }

    private fun loadOptions() {
        viewModelScope.launch {
            val result = reportRepository.options()
            if (result is Resource.Success) {
                mutable.value = mutable.value.copy(
                    classes = result.data.classGroups,
                    teachers = result.data.teachers
                )
            }
        }
    }

    fun setClassGroup(id: Int?) {
        if (mutable.value.classGroupId == id) return
        mutable.value = mutable.value.copy(classGroupId = id)
        refresh()
    }

    fun setTeacher(id: Int?) {
        if (mutable.value.teacherId == id) return
        mutable.value = mutable.value.copy(teacherId = id)
        refresh()
    }

    fun setStart(date: LocalDate?) {
        if (date != null && mutable.value.endDate != null && date.isAfter(mutable.value.endDate)) return
        mutable.value = mutable.value.copy(startDate = date)
        refresh()
    }

    fun setEnd(date: LocalDate?) {
        if (date != null && mutable.value.startDate != null && date.isBefore(mutable.value.startDate)) return
        mutable.value = mutable.value.copy(endDate = date)
        refresh()
    }

    fun clearFilters() {
        mutable.value = mutable.value.copy(
            startDate = null,
            endDate = null,
            classGroupId = null,
            teacherId = null
        )
        refresh()
    }

    fun refresh() { fetch(1) }
    fun nextPage() { if (mutable.value.canLoadMore) fetch(mutable.value.page + 1) }

    private fun fetch(nextPage: Int) {
        job?.cancel()
        job = viewModelScope.launch {
            val old = mutable.value
            mutable.value = if (nextPage == 1) old.copy(items = emptyList(), page = 0, totalPages = 0, total = 0, loading = true, error = null)
                            else old.copy(loading = true, error = null)
            try {
                val result = repository.getPage(
                    page = nextPage,
                    startDate = old.startDate?.toString(),
                    endDate = old.endDate?.toString(),
                    teacherId = old.teacherId,
                    classGroupId = old.classGroupId
                )
                val items = if (nextPage == 1) result.rows else mutable.value.items + result.rows
                mutable.value = mutable.value.copy(
                    items = items.distinctBy { it.id }, page = result.page,
                    totalPages = result.totalPages, total = result.total, loading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is IOException -> "Tidak dapat menghubungi server. Periksa koneksi internet Anda."
                    is HttpException -> e.serverMessage() ?: when (e.code()) {
                        401 -> "Sesi login Anda telah berakhir. Silakan login kembali."
                        403 -> "Halaman ini hanya dapat diakses oleh Admin."
                        else -> "Gagal memuat jurnal mengajar. Silakan coba lagi."
                    }
                    else -> e.message ?: "Terjadi kesalahan saat memuat jurnal."
                }
                mutable.value = mutable.value.copy(loading = false, error = message)
            }
        }
    }
}
