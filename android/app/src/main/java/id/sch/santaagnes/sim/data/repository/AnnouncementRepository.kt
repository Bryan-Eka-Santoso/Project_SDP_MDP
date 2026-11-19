package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.core.UploadFiles
import id.sch.santaagnes.sim.data.local.AnnouncementEntity
import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AnnouncementDto
import id.sch.santaagnes.sim.data.remote.ClassApi
import id.sch.santaagnes.sim.data.remote.CreateAnnouncementRequest
import id.sch.santaagnes.sim.data.remote.GradesApi
import id.sch.santaagnes.sim.data.remote.SchoolInfoApi
import id.sch.santaagnes.sim.data.remote.UpdateAnnouncementRequest
import id.sch.santaagnes.sim.domain.model.Announcement
import id.sch.santaagnes.sim.domain.model.AnnouncementFeed
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.UploadedAttachment
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Repository pengumuman sekolah & kelas binaan (F-PGM-01, F-PGM-02, F-PGM-03) dengan cache Room. */
interface AnnouncementRepository {
    suspend fun currentRole(): String?

    suspend fun homeroomClasses(): List<NamedId>

    suspend fun availableClasses(): List<NamedId>

    suspend fun getAnnouncements(
        scope: String? = null,
        classGroupId: Int? = null,
        studentId: Int? = null,
        search: String? = null
    ): Resource<AnnouncementFeed>

    suspend fun attachmentName(uri: String): String?

    suspend fun uploadAttachment(uri: String): Resource<UploadedAttachment>

    suspend fun createAnnouncement(
        title: String,
        content: String,
        scope: String,
        classGroupId: Int?,
        date: String?,
        attachmentUrl: String?,
        attachmentPublicId: String?
    ): Resource<Announcement>

    suspend fun updateAnnouncement(
        id: Int,
        title: String,
        content: String,
        date: String,
        attachmentUrl: String?,
        attachmentPublicId: String?,
        clearAttachment: Boolean = false
    ): Resource<Announcement>

    suspend fun deleteAnnouncement(id: Int): Resource<Unit>
}

@Singleton
class AnnouncementRepositoryImpl @Inject constructor(
    private val api: SchoolInfoApi,
    private val classApi: ClassApi,
    private val gradesApi: GradesApi,
    private val localDao: LocalDao,
    private val session: SessionManager,
    private val uploads: UploadFiles,
    private val errorMapper: ApiErrorMapper
) : AnnouncementRepository {

    override suspend fun currentRole(): String? = session.getRole()

    override suspend fun homeroomClasses(): List<NamedId> = session.getHomeroomClassGroups()

    override suspend fun availableClasses(): List<NamedId> {
        val role = session.getRole()
        if (role == "ADMIN") {
            val classes = runCatching {
                classApi.getClasses(page = 1, limit = 100).data.orEmpty()
            }.getOrDefault(emptyList())
            val active = classes.filter { it.academicYear.isActive }.ifEmpty { classes }
            return active.map { NamedId(it.id, it.name) }
        }
        if (role == "TEACHER") {
            val homeroom = session.getHomeroomClassGroups()
            val taught = runCatching {
                gradesApi.assignments().data.orEmpty().mapNotNull { dto ->
                    val name = dto.className?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    NamedId(dto.classGroupId, name)
                }
            }.getOrDefault(emptyList())
            return (homeroom + taught)
                .distinctBy { it.id }
                .sortedBy { it.name }
        }
        return emptyList()
    }

    override suspend fun getAnnouncements(
        scope: String?,
        classGroupId: Int?,
        studentId: Int?,
        search: String?
    ): Resource<AnnouncementFeed> {
        val remote = errorMapper.call {
            val data = api.getAnnouncements(
                page = 1,
                limit = 50,
                scope = scope,
                classGroupId = classGroupId,
                studentId = studentId,
                search = search?.takeIf { it.isNotBlank() }
            ).data ?: error("Gagal memuat pengumuman.")
            data.map { it.toDomain() }
        }

        val isUnfiltered =
            scope == null && classGroupId == null && studentId == null && search.isNullOrBlank()
        val accountId = session.getUserId()

        return when (remote) {
            is Resource.Success -> {
                if (isUnfiltered && accountId != null) {
                    localDao.clearAnnouncements(accountId)
                    localDao.saveAnnouncements(
                        remote.data.map { item ->
                            AnnouncementEntity(
                                accountId = accountId,
                                announcementId = item.id,
                                title = item.title,
                                content = item.content,
                                publishedAt = item.date,
                                attachmentUrl = item.attachmentUrl
                            )
                        }
                    )
                }
                Resource.Success(AnnouncementFeed(items = remote.data, isFromCache = false))
            }
            is Resource.Error -> {
                if (isUnfiltered && accountId != null) {
                    val cached = localDao.getAnnouncements(accountId).first()
                    if (cached.isNotEmpty()) {
                        return Resource.Success(
                            AnnouncementFeed(
                                items = cached.map { entity ->
                                    Announcement(
                                        id = entity.announcementId,
                                        title = entity.title,
                                        content = entity.content,
                                        scope = "SCHOOL",
                                        authorName = "Tersimpan offline",
                                        date = entity.publishedAt,
                                        attachmentUrl = entity.attachmentUrl
                                    )
                                },
                                isFromCache = true
                            )
                        )
                    }
                }
                remote
            }
        }
    }

    override suspend fun attachmentName(uri: String): String? = uploads.name(uri)

    override suspend fun uploadAttachment(uri: String): Resource<UploadedAttachment> = errorMapper.call {
        val part = uploads.attachmentPart(uri)
        val dto = api.uploadAnnouncementAttachment(part).data
            ?: error("Gagal mengunggah lampiran pengumuman.")
        UploadedAttachment(
            url = dto.url,
            publicId = dto.publicId,
            fileName = dto.fileName
        )
    }

    override suspend fun createAnnouncement(
        title: String,
        content: String,
        scope: String,
        classGroupId: Int?,
        date: String?,
        attachmentUrl: String?,
        attachmentPublicId: String?
    ): Resource<Announcement> = errorMapper.call {
        val dto = api.createAnnouncement(
            CreateAnnouncementRequest(
                title = title,
                content = content,
                scope = scope,
                classGroupId = classGroupId,
                date = date,
                attachmentUrl = attachmentUrl,
                attachmentPublicId = attachmentPublicId
            )
        ).data ?: error("Gagal membuat pengumuman.")
        dto.toDomain()
    }

    override suspend fun updateAnnouncement(
        id: Int,
        title: String,
        content: String,
        date: String,
        attachmentUrl: String?,
        attachmentPublicId: String?,
        clearAttachment: Boolean
    ): Resource<Announcement> = errorMapper.call {
        val dto = api.updateAnnouncement(
            id = id,
            body = UpdateAnnouncementRequest(
                title = title,
                content = content,
                date = date,
                attachmentUrl = if (clearAttachment) "" else attachmentUrl,
                attachmentPublicId = if (clearAttachment) "" else attachmentPublicId
            )
        ).data ?: error("Gagal memperbarui pengumuman.")
        dto.toDomain()
    }

    override suspend fun deleteAnnouncement(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(api.deleteAnnouncement(id))
    }

    private fun AnnouncementDto.toDomain(): Announcement = Announcement(
        id = id,
        title = title,
        content = content,
        scope = scope,
        classGroupId = classGroupId,
        className = classGroup?.name,
        authorName = author?.name ?: "-",
        date = date,
        attachmentUrl = attachmentUrl,
        attachmentPublicId = attachmentPublicId
    )
}
