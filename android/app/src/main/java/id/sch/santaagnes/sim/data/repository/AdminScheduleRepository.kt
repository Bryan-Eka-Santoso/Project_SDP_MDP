package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.core.TemplateFiles
import id.sch.santaagnes.sim.core.UploadFiles
import id.sch.santaagnes.sim.data.remote.AdminScheduleApi
import id.sch.santaagnes.sim.data.remote.MasterLookupApi
import id.sch.santaagnes.sim.data.remote.SaveChangeRequest
import id.sch.santaagnes.sim.data.remote.SlotRequest
import id.sch.santaagnes.sim.domain.model.AdminChange
import id.sch.santaagnes.sim.domain.model.AdminSlot
import id.sch.santaagnes.sim.domain.model.AssignmentOption
import id.sch.santaagnes.sim.domain.model.ChangeInput
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ImportResult
import id.sch.santaagnes.sim.domain.model.PageResult
import id.sch.santaagnes.sim.domain.model.RoomOption
import id.sch.santaagnes.sim.domain.model.ScheduleLookups
import id.sch.santaagnes.sim.domain.model.SlotFilter
import id.sch.santaagnes.sim.domain.model.SlotInput
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Kelola jadwal (F-JDW-01..04). Hanya online; hanya dipakai admin, backend tetap memeriksa hak aksesnya. */
interface AdminScheduleRepository {

    suspend fun slots(page: Int, filter: SlotFilter): Resource<PageResult<AdminSlot>>

    /** Semua slot semester aktif (semua halaman), untuk pilihan slot dan ruangan. */
    suspend fun allSlots(): Resource<List<AdminSlot>>

    suspend fun createSlot(input: SlotInput): Resource<AdminSlot>

    suspend fun updateSlot(slotId: Int, input: SlotInput): Resource<AdminSlot>

    suspend fun deleteSlot(slotId: Int): Resource<Unit>

    /** Nama berkas dari pemilih file (uri teks), atau null bila tidak terbaca. */
    suspend fun fileName(uri: String): String?

    suspend fun importSlots(uri: String): Resource<ImportResult>

    /** Simpan template impor jadwal ke lokasi [destination] (uri teks dari pemilih file); true bila berhasil. */
    suspend fun saveTemplate(destination: String): Boolean

    suspend fun changes(page: Int, type: ChangeType?): Resource<PageResult<AdminChange>>

    suspend fun saveChange(input: ChangeInput): Resource<AdminChange>

    suspend fun deleteChange(changeId: Int): Resource<Unit>

    /** Penugasan (dari master Yosua) dan ruangan yang dikenal dari slot yang sudah ada. */
    suspend fun lookups(): Resource<ScheduleLookups>
}

@Singleton
class AdminScheduleRepositoryImpl @Inject constructor(
    private val api: AdminScheduleApi,
    private val master: MasterLookupApi,
    private val errors: ApiErrorMapper,
    private val uploads: UploadFiles,
    private val templates: TemplateFiles
) : AdminScheduleRepository {

    override suspend fun slots(page: Int, filter: SlotFilter): Resource<PageResult<AdminSlot>> = errors.call {
        val response = api.getSlots(
            page = page,
            limit = PAGE_SIZE,
            classGroupId = filter.classGroupId,
            teacherId = filter.teacherId,
            dayOfWeek = filter.dayOfWeek?.name
        )
        check(response.success) { "Jadwal gagal dimuat." }
        PageResult(
            items = response.data.orEmpty().map { it.toDomain() },
            page = page,
            hasMore = page < (response.meta?.totalPages ?: page)
        )
    }

    override suspend fun allSlots(): Resource<List<AdminSlot>> = errors.call { loadAllSlots() }

    override suspend fun createSlot(input: SlotInput): Resource<AdminSlot> = errors.call {
        checkNotNull(api.createSlot(input.toRequest()).data) { "Jadwal gagal disimpan." }.toDomain()
    }

    override suspend fun updateSlot(slotId: Int, input: SlotInput): Resource<AdminSlot> = errors.call {
        checkNotNull(api.updateSlot(slotId, input.toRequest()).data) { "Jadwal gagal disimpan." }.toDomain()
    }

    override suspend fun deleteSlot(slotId: Int): Resource<Unit> = errors.call {
        val response = api.deleteSlot(slotId)
        // 404 = sudah terhapus (mis. dari perangkat lain); hasilnya sama dengan yang diminta.
        if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
    }

    override suspend fun fileName(uri: String): String? = uploads.name(uri)

    override suspend fun saveTemplate(destination: String): Boolean = templates.save("jadwal.xlsx", destination)

    override suspend fun importSlots(uri: String): Resource<ImportResult> = errors.call {
        val part = uploads.xlsxPart(uri, "jadwal.xlsx")
        val data = checkNotNull(api.importSlots(part).data) { "Impor gagal." }
        ImportResult(data.imported)
    }

    override suspend fun changes(page: Int, type: ChangeType?): Resource<PageResult<AdminChange>> = errors.call {
        val response = api.getChanges(page, PAGE_SIZE, type?.name)
        check(response.success) { "Perubahan jadwal gagal dimuat." }
        PageResult(
            items = response.data.orEmpty().map { it.toDomain() },
            page = page,
            hasMore = page < (response.meta?.totalPages ?: page)
        )
    }

    override suspend fun saveChange(input: ChangeInput): Resource<AdminChange> = errors.call {
        val request = SaveChangeRequest(
            scheduleSlotId = input.slotId,
            date = input.date.toString(),
            type = input.type.name,
            description = input.description.trim(),
            substituteTeacherId = input.substituteTeacherId,
            newRoomId = input.newRoomId,
            newStartTime = input.newStartTime?.toString(),
            newEndTime = input.newEndTime?.toString()
        )
        checkNotNull(api.saveChange(request).data) { "Perubahan gagal disimpan." }.toDomain()
    }

    override suspend fun deleteChange(changeId: Int): Resource<Unit> = errors.call {
        val response = api.deleteChange(changeId)
        if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
    }

    override suspend fun lookups(): Resource<ScheduleLookups> = errors.call {
        val semesterId = checkNotNull(master.getActiveSemester().data) { "Tidak ada semester aktif." }.id
        val assignments = mutableListOf<AssignmentOption>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response = master.getTeachingAssignments(page, LOOKUP_PAGE_SIZE, semesterId)
            assignments += response.data.orEmpty().map { it.toDomain() }
            if (page >= (response.meta?.totalPages ?: page)) break
            page += 1
        }
        // Ruangan belum punya endpoint (master fasilitas milik Bryan): diambil dari slot yang sudah ada.
        val rooms = loadAllSlots()
            .mapNotNull { slot -> slot.roomId?.let { RoomOption(it, slot.roomName.orEmpty()) } }
            .distinctBy { it.id }
            .sortedBy { it.name }
        ScheduleLookups(
            assignments = assignments.sortedWith(compareBy({ it.classGroupName }, { it.subjectName })),
            rooms = rooms
        )
    }

    private suspend fun loadAllSlots(): List<AdminSlot> {
        val all = mutableListOf<AdminSlot>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response = api.getSlots(page, LOOKUP_PAGE_SIZE, null, null, null)
            all += response.data.orEmpty().map { it.toDomain() }
            if (page >= (response.meta?.totalPages ?: page)) break
            page += 1
        }
        return all
    }

    private fun SlotInput.toRequest() = SlotRequest(
        teachingAssignmentId = assignmentId,
        roomId = roomId,
        dayOfWeek = dayOfWeek.name,
        startTime = startTime.toString(),
        endTime = endTime.toString()
    )

    private companion object {
        const val PAGE_SIZE = 20
        const val LOOKUP_PAGE_SIZE = 100
        // Batas halaman menjaga dari perulangan tak terbatas bila server berperilaku aneh.
        const val MAX_PAGES = 20
    }
}
