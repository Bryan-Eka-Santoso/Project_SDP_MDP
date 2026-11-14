package id.sch.santaagnes.sim.ui.admin

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepository
import id.sch.santaagnes.sim.domain.model.AdminChange
import id.sch.santaagnes.sim.domain.model.AdminSlot
import id.sch.santaagnes.sim.domain.model.AssignmentOption
import id.sch.santaagnes.sim.domain.model.ChangeInput
import id.sch.santaagnes.sim.domain.model.ChangeType
import id.sch.santaagnes.sim.domain.model.ImportResult
import id.sch.santaagnes.sim.domain.model.ImportRowError
import id.sch.santaagnes.sim.domain.model.PageResult
import id.sch.santaagnes.sim.domain.model.RoomOption
import id.sch.santaagnes.sim.domain.model.ScheduleLookups
import id.sch.santaagnes.sim.domain.model.SlotFilter
import id.sch.santaagnes.sim.domain.model.SlotInput
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class FakeAdminScheduleRepository : AdminScheduleRepository {
    var slotPages: List<List<AdminSlot>> = listOf(emptyList())
    var changePages: List<List<AdminChange>> = listOf(emptyList())
    var slotsError: String? = null
    var changesError: String? = null
    var saveError: String? = null
    var deleteError: String? = null
    var importError: String? = null
    var importErrorRows: List<ImportRowError> = emptyList()
    var templateSaveResult = true
    var importedCount = 0
    var fileName: String? = "jadwal.xlsx"
    var lookupsResult: Resource<ScheduleLookups> = Resource.Success(sampleLookups())

    val requestedSlotFilters = mutableListOf<Pair<Int, SlotFilter>>()
    val requestedChangePages = mutableListOf<Pair<Int, ChangeType?>>()
    val createdSlots = mutableListOf<SlotInput>()
    val savedTemplates = mutableListOf<String>()
    val updatedSlots = mutableListOf<Pair<Int, SlotInput>>()
    val deletedSlots = mutableListOf<Int>()
    val savedChanges = mutableListOf<ChangeInput>()
    val deletedChanges = mutableListOf<Int>()
    val imports = mutableListOf<String>()

    override suspend fun slots(page: Int, filter: SlotFilter): Resource<PageResult<AdminSlot>> {
        requestedSlotFilters += page to filter
        slotsError?.let { return Resource.Error(it) }
        return Resource.Success(PageResult(slotPages.getOrElse(page - 1) { emptyList() }, page, page < slotPages.size))
    }

    override suspend fun allSlots(): Resource<List<AdminSlot>> {
        slotsError?.let { return Resource.Error(it) }
        return Resource.Success(slotPages.flatten())
    }

    override suspend fun createSlot(input: SlotInput): Resource<AdminSlot> {
        saveError?.let { return Resource.Error(it) }
        createdSlots += input
        return Resource.Success(slot(99))
    }

    override suspend fun updateSlot(slotId: Int, input: SlotInput): Resource<AdminSlot> {
        saveError?.let { return Resource.Error(it) }
        updatedSlots += slotId to input
        return Resource.Success(slot(slotId))
    }

    override suspend fun deleteSlot(slotId: Int): Resource<Unit> {
        deleteError?.let { return Resource.Error(it) }
        deletedSlots += slotId
        return Resource.Success(Unit)
    }

    override suspend fun fileName(uri: String): String? = fileName

    override suspend fun importSlots(uri: String): Resource<ImportResult> {
        importError?.let { return Resource.Error(it, importErrorRows) }
        imports += uri
        return Resource.Success(ImportResult(importedCount))
    }

    override suspend fun saveTemplate(destination: String): Boolean {
        savedTemplates += destination
        return templateSaveResult
    }

    override suspend fun changes(page: Int, type: ChangeType?): Resource<PageResult<AdminChange>> {
        requestedChangePages += page to type
        changesError?.let { return Resource.Error(it) }
        return Resource.Success(PageResult(changePages.getOrElse(page - 1) { emptyList() }, page, page < changePages.size))
    }

    override suspend fun saveChange(input: ChangeInput): Resource<AdminChange> {
        saveError?.let { return Resource.Error(it) }
        savedChanges += input
        return Resource.Success(change(99))
    }

    override suspend fun deleteChange(changeId: Int): Resource<Unit> {
        deleteError?.let { return Resource.Error(it) }
        deletedChanges += changeId
        return Resource.Success(Unit)
    }

    override suspend fun lookups(): Resource<ScheduleLookups> = lookupsResult

    companion object {
        fun slot(id: Int) = AdminSlot(
            id = id,
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(8, 30),
            roomId = 1,
            roomName = "R-101",
            assignmentId = 7,
            teacherId = 3,
            teacherName = "Guru Satu",
            subjectName = "Matematika",
            classGroupId = 5,
            classGroupName = "X-1"
        )

        fun change(id: Int) = AdminChange(
            id = id,
            date = LocalDate.of(2026, 11, 16),
            type = ChangeType.CANCEL,
            description = "Guru berhalangan.",
            substituteTeacherId = null,
            substituteTeacherName = null,
            newRoomId = null,
            newRoomName = null,
            newStartTime = null,
            newEndTime = null,
            slot = slot(1)
        )

        fun sampleLookups() = ScheduleLookups(
            assignments = listOf(
                AssignmentOption(7, 5, "X-1", "Matematika", 3, "Guru Satu"),
                AssignmentOption(8, 6, "X-2", "Fisika", 4, "Guru Dua")
            ),
            rooms = listOf(RoomOption(1, "R-101"), RoomOption(2, "R-102"))
        )
    }
}
