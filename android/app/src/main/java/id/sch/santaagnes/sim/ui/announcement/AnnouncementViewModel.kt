package id.sch.santaagnes.sim.ui.announcement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AnnouncementRepository
import id.sch.santaagnes.sim.domain.model.Announcement
import id.sch.santaagnes.sim.domain.model.NamedId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnnouncementUiState(
    val role: String? = null,
    val homeroomClasses: List<NamedId> = emptyList(),
    val filterClasses: List<NamedId> = emptyList(),
    val items: List<Announcement> = emptyList(),
    val selectedScopeFilter: String? = null,
    val selectedClassFilterId: Int? = null,
    val searchQuery: String = "",
    val isFromCache: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val isAdmin: Boolean get() = role == "ADMIN"
    val canCreate: Boolean get() = isAdmin || (role == "TEACHER" && homeroomClasses.isNotEmpty())
    val homeroomClassIds: Set<Int> get() = homeroomClasses.map { it.id }.toSet()
    val canFilterByClass: Boolean
        get() = selectedScopeFilter == "CLASS" &&
            filterClasses.isNotEmpty() &&
            (isAdmin || role == "TEACHER")

    fun formTargetClasses(): List<NamedId> = if (isAdmin) filterClasses else homeroomClasses

    fun canManage(item: Announcement): Boolean = when {
        isAdmin -> true
        role == "TEACHER" && item.scope == "CLASS" && item.classGroupId != null ->
            item.classGroupId in homeroomClassIds
        else -> false
    }
}

/** Mengelola pengumuman sekolah & kelas (Admin), kelas binaan (Wali Kelas), & daftar (Semua). */
@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val repository: AnnouncementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnnouncementUiState())
    val uiState: StateFlow<AnnouncementUiState> = _uiState.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            val role = repository.currentRole()
            val homeroom = if (role == "TEACHER") repository.homeroomClasses() else emptyList()
            val classes = repository.availableClasses()
            _uiState.update {
                it.copy(
                    role = role,
                    homeroomClasses = homeroom,
                    filterClasses = classes
                )
            }
            load()
        }
    }

    fun setScopeFilter(scope: String?) {
        val nextClassFilter = if (scope == "CLASS") _uiState.value.selectedClassFilterId else null
        _uiState.update {
            it.copy(selectedScopeFilter = scope, selectedClassFilterId = nextClassFilter)
        }
        load(
            scope = scope,
            classGroupId = nextClassFilter,
            search = _uiState.value.searchQuery
        )
    }

    fun setClassFilter(classGroupId: Int?) {
        _uiState.update {
            it.copy(selectedScopeFilter = "CLASS", selectedClassFilterId = classGroupId)
        }
        load(
            scope = "CLASS",
            classGroupId = classGroupId,
            search = _uiState.value.searchQuery
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        load(
            scope = _uiState.value.selectedScopeFilter,
            classGroupId = _uiState.value.selectedClassFilterId,
            search = query
        )
    }

    fun load(
        scope: String? = _uiState.value.selectedScopeFilter,
        classGroupId: Int? = _uiState.value.selectedClassFilterId,
        search: String = _uiState.value.searchQuery
    ) {
        val effectiveClassId = if (scope == "CLASS") classGroupId else null
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (
                val res = repository.getAnnouncements(
                    scope = scope,
                    classGroupId = effectiveClassId,
                    search = search
                )
            ) {
                is Resource.Success -> _uiState.update {
                    it.copy(
                        items = res.data.items,
                        isFromCache = res.data.isFromCache,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    _message.emit(res.message)
                }
            }
        }
    }

    suspend fun resolveAttachmentName(uri: String): String? = repository.attachmentName(uri)

    fun saveAnnouncement(
        editingId: Int?,
        title: String,
        content: String,
        scope: String,
        classGroupId: Int?,
        date: String,
        pickedLocalUri: String?,
        existingUrl: String?,
        existingPublicId: String?,
        clearAttachment: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }

            var attachmentUrl = if (clearAttachment) null else existingUrl
            var attachmentPublicId = if (clearAttachment) null else existingPublicId

            if (pickedLocalUri != null) {
                when (val uploadRes = repository.uploadAttachment(pickedLocalUri)) {
                    is Resource.Success -> {
                        attachmentUrl = uploadRes.data.url
                        attachmentPublicId = uploadRes.data.publicId
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        _message.emit(uploadRes.message)
                        return@launch
                    }
                }
            }

            val res = if (editingId == null) {
                repository.createAnnouncement(
                    title = title,
                    content = content,
                    scope = scope,
                    classGroupId = if (scope == "CLASS") classGroupId else null,
                    date = date,
                    attachmentUrl = attachmentUrl,
                    attachmentPublicId = attachmentPublicId
                )
            } else {
                repository.updateAnnouncement(
                    id = editingId,
                    title = title,
                    content = content,
                    date = date,
                    attachmentUrl = attachmentUrl,
                    attachmentPublicId = attachmentPublicId,
                    clearAttachment = clearAttachment && pickedLocalUri == null
                )
            }

            when (res) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(
                        if (editingId == null) {
                            "Pengumuman berhasil dikirim"
                        } else {
                            "Pengumuman berhasil diperbarui"
                        }
                    )
                    load()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(res.message)
                }
            }
        }
    }

    fun deleteAnnouncement(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            when (val res = repository.deleteAnnouncement(id)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit("Pengumuman berhasil dihapus")
                    load()
                }
                is Resource.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _message.emit(res.message)
                }
            }
        }
    }
}
