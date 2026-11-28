package id.sch.santaagnes.sim.ui.progress

import id.sch.santaagnes.sim.domain.model.GradeAssignment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.data.remote.ProgressNoteDto
import id.sch.santaagnes.sim.data.repository.ProgressNoteRepository
import id.sch.santaagnes.sim.data.repository.GradesRepository
import id.sch.santaagnes.sim.domain.model.GradeStudent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressNotesState(
    val loading: Boolean = false, val saving: Boolean = false,
    val items: List<ProgressNoteDto> = emptyList(), val error: String? = null,
    val page: Int = 0, val total: Int = 0, val students: List<GradeStudent> = emptyList(),
    val message: String? = null,
    /** Wali kelas membaca semua mapel kelas binaan; hanya penugasan sendiri yang bisa ditulis. */
    val homeroomOptions: List<GradeAssignment> = emptyList(),
    val readOnly: Boolean = false
) {
    val more: Boolean get() = !loading && !saving && items.size < total
}

@HiltViewModel
class StudentProgressNotesViewModel @Inject constructor(private val repo: ProgressNoteRepository): ViewModel() {
    private val _state = MutableStateFlow(ProgressNotesState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    init { refresh() }
    fun refresh() { job?.cancel(); _state.value = ProgressNotesState(); fetch(1) }
    fun more() { if (_state.value.more) fetch(_state.value.page + 1) }
    private fun fetch(page: Int) {
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val (data, meta) = repo.mine(page)
                _state.value = _state.value.copy(loading = false, items = if (page == 1) data else _state.value.items + data,
                    page = page, total = meta?.total ?: data.size)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = ProgressNoteRepository.message(e)) }
        }
    }
}

@HiltViewModel
class TeacherProgressNotesViewModel @Inject constructor(
    private val repo: ProgressNoteRepository, private val grades: GradesRepository
): ViewModel() {
    private val _state = MutableStateFlow(ProgressNotesState())
    val state = _state.asStateFlow()
    private var assignmentId = 0
    private var job: Job? = null
    /** Mode wali kelas: pengguna memilih mapel di antara penugasan kelas binaannya. */
    fun startHomeroom() {
        if (_state.value.homeroomOptions.isNotEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, readOnly = true)
            try {
                val options = grades.assignments(includeHomeroom = true).filter { it.isHomeroomClass }
                _state.value = _state.value.copy(loading = false, homeroomOptions = options)
                options.firstOrNull()?.let { selectHomeroom(it.id) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = ProgressNoteRepository.message(e)) }
        }
    }
    fun selectHomeroom(id: Int) {
        val option = _state.value.homeroomOptions.firstOrNull { it.id == id } ?: return
        if (id == assignmentId && _state.value.page > 0) return
        assignmentId = id
        _state.value = _state.value.copy(readOnly = !option.isOwn, students = emptyList())
        refresh()
    }
    fun start(id: Int) {
        if (id == assignmentId && _state.value.page > 0) return
        assignmentId = id
        refresh()
    }
    fun refresh() {
        if (assignmentId <= 0) return
        job?.cancel()
        _state.value = ProgressNotesState(
            students = _state.value.students,
            homeroomOptions = _state.value.homeroomOptions,
            readOnly = _state.value.readOnly
        )
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val (data, meta) = repo.assignment(assignmentId)
                val students = grades.students(assignmentId)
                _state.value = _state.value.copy(loading = false, items = data,
                    total = meta?.total ?: data.size, page = 1, students = students)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = ProgressNoteRepository.message(e)) }
        }
    }
    fun more() {
        if (!_state.value.more) return
        job = viewModelScope.launch {
            val page = _state.value.page + 1
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val (data, meta) = repo.assignment(assignmentId, page)
                _state.value = _state.value.copy(loading = false, items = _state.value.items + data,
                    page = page, total = meta?.total ?: _state.value.total)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = ProgressNoteRepository.message(e)) }
        }
    }
    fun save(studentId: Int, note: ProgressNoteDto?, date: String, content: String) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null, message = null)
            try {
                if (note == null) repo.create(assignmentId, studentId, date, content)
                else repo.update(note.id, date, content)
                _state.value = _state.value.copy(saving = false, message = "Catatan berhasil disimpan.")
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = ProgressNoteRepository.message(e))
            }
        }
    }
}
