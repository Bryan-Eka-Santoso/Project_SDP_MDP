package id.sch.santaagnes.sim.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.RiwayatPelanggaranApi
import id.sch.santaagnes.sim.data.repository.AccountRepository
import id.sch.santaagnes.sim.data.repository.AnnouncementRepository
import id.sch.santaagnes.sim.data.repository.AttendanceRepository
import id.sch.santaagnes.sim.data.repository.ScheduleRepository
import id.sch.santaagnes.sim.data.repository.TaskRepository
import id.sch.santaagnes.sim.domain.model.Announcement
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.ScheduleScope
import id.sch.santaagnes.sim.domain.model.Task
import id.sch.santaagnes.sim.ui.grades.GradesReportApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import java.time.LocalDate
import javax.inject.Inject

/** Angka ringkas di kartu beranda. null = belum termuat atau tidak tersedia (tampil "-"). */
data class HomeStats(
    val averageScore: Double? = null,
    val attendancePercent: Int? = null,
    val lessonsToday: Int? = null,
    val violationPoints: Int? = null
)

data class HomeUiState(
    // null sampai data sesi terbaca
    val role: String? = null,
    val name: String? = null,
    // kelas siswa (Figma: "XII IPA 1"); wali murid melihat nama anak di pemilih
    val className: String? = null,
    // wali murid: anak-anaknya dan yang sedang dilihat
    val children: List<NamedId> = emptyList(),
    val selectedChildId: Int? = null,
    val stats: HomeStats = HomeStats(),
    val announcements: List<Announcement> = emptyList(),
    val reminders: List<Task> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isGuardian: Boolean get() = role == "GUARDIAN"

    /** Wali murid tanpa anak terhubung tidak punya tugas yang bisa ditampilkan. */
    val hasNoChild: Boolean get() = isGuardian && children.isEmpty()

    val isRemindersEmpty: Boolean
        get() = reminders.isEmpty() && !isLoading && errorMessage == null && !hasNoChild
}

/** Beranda siswa dan wali murid: sapaan, kartu ringkasan, pengingat tugas terdekat, pintasan, dan pengumuman. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: SessionManager,
    private val tasks: TaskRepository,
    private val account: AccountRepository,
    private val schedule: ScheduleRepository,
    private val attendance: AttendanceRepository,
    private val announcements: AnnouncementRepository,
    private val violations: RiwayatPelanggaranApi,
    retrofit: Retrofit
) : ViewModel() {

    private val scores by lazy { retrofit.create(GradesReportApi::class.java) }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val role = session.getRole()
            val children = if (role == "GUARDIAN") session.getChildren() else emptyList()
            val active = session.getActiveStudentId()
            val selected = children.firstOrNull { it.id == active }?.id ?: children.firstOrNull()?.id
            _uiState.update {
                it.copy(role = role, name = session.getUserName(), children = children, selectedChildId = selected)
            }
            refresh()
        }
    }

    /** Muat ulang; dipanggil saat layar tampil agar tanda selesai dan tugas baru ikut terbaca. */
    fun refresh() {
        val state = _uiState.value
        if (state.role == null) return
        if (state.hasNoChild) {
            _uiState.update { it.copy(isLoading = false, reminders = emptyList(), stats = HomeStats(), announcements = emptyList()) }
            return
        }
        val child = state.selectedChildId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Ringkasan dan pengumuman bersifat pelengkap: gagal dimuat tidak mengganggu pengingat tugas.
            launch { loadSummary(state.role, child) }
            when (val result = tasks.reminders(studentId = child, limit = REMINDER_COUNT)) {
                is Resource.Success -> {
                    // Anak diganti selagi memuat: hasil lama dibuang.
                    if (_uiState.value.selectedChildId != child) return@launch
                    _uiState.update { it.copy(reminders = result.data, isLoading = false) }
                }
                is Resource.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun selectChild(childId: Int) {
        if (childId == _uiState.value.selectedChildId) return
        _uiState.update {
            it.copy(selectedChildId = childId, reminders = emptyList(), stats = HomeStats(), announcements = emptyList())
        }
        viewModelScope.launch { session.saveActiveStudentId(childId) }
        refresh()
    }

    private suspend fun loadSummary(role: String?, child: Int?) = coroutineScope {
        val isGuardian = role == "GUARDIAN"
        val studentId = if (isGuardian) child else session.getStudentId()
        val today = DateTimeWib.today()

        val average = async { runCatching { averageScore(isGuardian, child) }.getOrNull() }
        val presence = async { runCatching { attendancePercent(today, if (isGuardian) child else null) }.getOrNull() }
        val lessons = async { runCatching { lessonsOn(today, if (isGuardian) child else null) }.getOrNull() }
        val points = async { runCatching { violationPoints(studentId) }.getOrNull() }
        val news = async { runCatching { latestAnnouncements(if (isGuardian) child else null) }.getOrNull() }
        val profile = async { if (isGuardian) null else runCatching { account.refreshProfile() }.getOrNull() }

        val stats = HomeStats(average.await(), presence.await(), lessons.await(), points.await())
        // Wali murid: baris di bawah nama menunjukkan anak yang sedang dilihat.
        val className = if (isGuardian) {
            _uiState.value.children.firstOrNull { it.id == child }?.name
        } else {
            (profile.await() as? Resource.Success)?.data?.classNames?.firstOrNull()
        }
        // Anak diganti selagi memuat: hasil lama dibuang.
        if (_uiState.value.selectedChildId != child) return@coroutineScope
        _uiState.update {
            it.copy(
                stats = stats,
                announcements = news.await().orEmpty(),
                className = className ?: it.className
            )
        }
    }

    private suspend fun averageScore(isGuardian: Boolean, child: Int?): Double? {
        val subjects = if (isGuardian) {
            scores.studentScores(child ?: return null)
        } else {
            scores.ownScores()
        }.data.orEmpty()
        val finals = subjects.mapNotNull { it.finalScore }
        return if (finals.isEmpty()) null else finals.average()
    }

    private suspend fun attendancePercent(today: LocalDate, child: Int?): Int? {
        val start = today.withDayOfMonth(1).toString()
        val summary = if (child != null) {
            attendance.studentSummary(child, start, today.toString())
        } else {
            attendance.mySummary(start, today.toString())
        }
        return summary.attendancePercentage?.let { Math.round(it).toInt() }
    }

    // Pertemuan yang dibatalkan atau dipindah dari hari ini tidak dihitung.
    private suspend fun lessonsOn(today: LocalDate, child: Int?): Int? {
        val scope = if (child != null) ScheduleScope.Child(child) else ScheduleScope.Own
        if (schedule.refresh(scope, today, today) is Resource.Error) return null
        return schedule.observe(scope, today, today).first().count { !it.isAway }
    }

    private suspend fun violationPoints(studentId: Int?): Int? {
        val id = studentId ?: return null
        return violations.getRiwayat(id).data?.totalPoints
    }

    private suspend fun latestAnnouncements(child: Int?): List<Announcement> =
        when (val result = this.announcements.getAnnouncements(studentId = child)) {
            is Resource.Success -> result.data.items.take(ANNOUNCEMENT_COUNT)
            is Resource.Error -> emptyList()
        }

    private companion object {
        const val REMINDER_COUNT = 3
        const val ANNOUNCEMENT_COUNT = 3
    }
}
