package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AcademicEventDto
import id.sch.santaagnes.sim.data.remote.CreateAcademicEventRequest
import id.sch.santaagnes.sim.data.remote.SchoolInfoApi
import id.sch.santaagnes.sim.data.remote.UpdateAcademicEventRequest
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import javax.inject.Inject
import javax.inject.Singleton

/** Repository kalender akademik sekolah (F-KAL-01 kelola admin, F-KAL-02 baca semua user). */
interface CalendarRepository {
    suspend fun currentRole(): String?

    suspend fun getEvents(
        type: String? = null,
        search: String? = null
    ): Resource<List<AcademicEvent>>

    suspend fun createEvent(
        title: String,
        type: AcademicEventType,
        startDate: String,
        endDate: String,
        description: String?
    ): Resource<AcademicEvent>

    suspend fun updateEvent(
        id: Int,
        title: String,
        type: AcademicEventType,
        startDate: String,
        endDate: String,
        description: String?
    ): Resource<AcademicEvent>

    suspend fun deleteEvent(id: Int): Resource<Unit>
}

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val api: SchoolInfoApi,
    private val session: SessionManager,
    private val errorMapper: ApiErrorMapper
) : CalendarRepository {

    override suspend fun currentRole(): String? = session.getRole()

    override suspend fun getEvents(
        type: String?,
        search: String?
    ): Resource<List<AcademicEvent>> = errorMapper.call {
        val list = api.getAcademicEvents(
            page = 1,
            limit = 100,
            type = type,
            search = search?.takeIf { it.isNotBlank() }
        ).data ?: error("Gagal memuat kalender akademik.")
        list.map { it.toDomain() }
    }

    override suspend fun createEvent(
        title: String,
        type: AcademicEventType,
        startDate: String,
        endDate: String,
        description: String?
    ): Resource<AcademicEvent> = errorMapper.call {
        val dto = api.createAcademicEvent(
            CreateAcademicEventRequest(
                title = title,
                type = type.wire,
                startDate = startDate,
                endDate = endDate,
                description = description?.takeIf { it.isNotBlank() }
            )
        ).data ?: error("Gagal menambah agenda kalender.")
        dto.toDomain()
    }

    override suspend fun updateEvent(
        id: Int,
        title: String,
        type: AcademicEventType,
        startDate: String,
        endDate: String,
        description: String?
    ): Resource<AcademicEvent> = errorMapper.call {
        val dto = api.updateAcademicEvent(
            id = id,
            body = UpdateAcademicEventRequest(
                title = title,
                type = type.wire,
                startDate = startDate,
                endDate = endDate,
                description = description?.takeIf { it.isNotBlank() }
            )
        ).data ?: error("Gagal memperbarui agenda kalender.")
        dto.toDomain()
    }

    override suspend fun deleteEvent(id: Int): Resource<Unit> = errorMapper.call {
        ensureDeleteSucceeded(api.deleteAcademicEvent(id))
    }

    private fun AcademicEventDto.toDomain(): AcademicEvent = AcademicEvent(
        id = id,
        title = title,
        type = AcademicEventType.fromWire(type),
        startDate = startDate,
        endDate = endDate,
        description = description
    )
}
