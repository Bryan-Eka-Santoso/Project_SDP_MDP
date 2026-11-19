package id.sch.santaagnes.sim.ui.announcement

import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AnnouncementRepository
import id.sch.santaagnes.sim.data.repository.CalendarRepository
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import id.sch.santaagnes.sim.domain.model.Announcement
import id.sch.santaagnes.sim.domain.model.AnnouncementFeed
import id.sch.santaagnes.sim.domain.model.NamedId
import id.sch.santaagnes.sim.domain.model.UploadedAttachment
import id.sch.santaagnes.sim.ui.calendar.AcademicCalendarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolInfoViewModelsTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun adminCanCreateSchoolAndClassAnnouncementAndFilterByClass() = runTest {
        val allClasses = listOf(NamedId(5, "X-1"), NamedId(6, "XI-A"))
        val repo = FakeAnnouncementRepository(role = "ADMIN", available = allClasses)
        val vm = AnnouncementViewModel(repo)

        assertTrue(vm.uiState.value.canCreate)
        assertTrue(vm.uiState.value.canManage(repo.items.first()))
        assertTrue(vm.uiState.value.canManage(repo.items[1]))
        assertEquals(allClasses, vm.uiState.value.formTargetClasses())

        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { messages.add(it) } }

        vm.saveAnnouncement(
            editingId = null,
            title = "Pengumuman Baru",
            content = "Harap dibaca.",
            scope = "SCHOOL",
            classGroupId = null,
            date = "2026-11-18",
            pickedLocalUri = "content://doc/lampiran.png",
            existingUrl = null,
            existingPublicId = null,
            clearAttachment = false
        )

        // Admin juga dapat mengirim pengumuman untuk kelas spesifik
        vm.saveAnnouncement(
            editingId = null,
            title = "Info Kelas XI-A",
            content = "Bawa kalkulator.",
            scope = "CLASS",
            classGroupId = 6,
            date = "2026-11-19",
            pickedLocalUri = null,
            existingUrl = null,
            existingPublicId = null,
            clearAttachment = false
        )
        job.cancel()

        assertEquals(listOf("content://doc/lampiran.png"), repo.uploadedUris)
        assertEquals(4, vm.uiState.value.items.size)
        assertEquals("https://res.cloudinary.com/demo/lampiran.png", vm.uiState.value.items[2].attachmentUrl)
        assertEquals("CLASS", vm.uiState.value.items.last().scope)
        assertEquals(6, vm.uiState.value.items.last().classGroupId)
        assertTrue(messages.contains("Pengumuman berhasil dikirim"))

        // Filter kelas spesifik saat tab Kelas dipilih
        vm.setScopeFilter("CLASS")
        assertTrue(vm.uiState.value.canFilterByClass)
        assertEquals(2, vm.uiState.value.items.size)

        vm.setClassFilter(6)
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals(6, vm.uiState.value.items.first().classGroupId)

        // Ganti ke Sekolah mereset filter kelas
        vm.setScopeFilter("SCHOOL")
        assertNull(vm.uiState.value.selectedClassFilterId)
        assertFalse(vm.uiState.value.canFilterByClass)
    }

    @Test
    fun uploadFailureAbortsAnnouncementCreateWithoutMutatingList() = runTest {
        val repo = FakeAnnouncementRepository(role = "TEACHER", homeroom = listOf(NamedId(5, "X-1"))).apply {
            uploadError = "Layanan penyimpanan Cloudinary belum dikonfigurasi pada server."
        }
        val vm = AnnouncementViewModel(repo)

        assertTrue(vm.uiState.value.canCreate)
        assertTrue(vm.uiState.value.canManage(repo.items[1]))
        assertFalse(vm.uiState.value.canManage(repo.items[0]))

        val messages = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { messages.add(it) } }

        vm.saveAnnouncement(
            editingId = null,
            title = "Info Kelas",
            content = "Tugas prakarya.",
            scope = "CLASS",
            classGroupId = 5,
            date = "2026-11-18",
            pickedLocalUri = "content://doc/foto.jpg",
            existingUrl = null,
            existingPublicId = null,
            clearAttachment = false
        )
        job.cancel()

        assertEquals(2, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.isSubmitting)
        assertTrue(messages.first().contains("Cloudinary"))
    }

    @Test
    fun nonHomeroomTeacherAndStudentAreReadOnlyForAnnouncements() = runTest {
        val teacherVm = AnnouncementViewModel(FakeAnnouncementRepository(role = "TEACHER", homeroom = emptyList()))
        assertFalse(teacherVm.uiState.value.canCreate)

        val studentRepo = FakeAnnouncementRepository(role = "STUDENT").apply {
            isFromCache = true
        }
        val studentVm = AnnouncementViewModel(studentRepo)
        assertFalse(studentVm.uiState.value.canCreate)
        assertTrue(studentVm.uiState.value.isFromCache)
    }

    @Test
    fun academicCalendarControlsAdminPermissionsFiltersAndMonthGrid() = runTest {
        val repo = FakeCalendarRepository(role = "ADMIN")
        val vm = AcademicCalendarViewModel(repo)

        assertTrue(vm.uiState.value.isAdmin)
        assertEquals(2, vm.uiState.value.events.size)

        vm.setTypeFilter(AcademicEventType.EXAM)
        assertEquals(1, vm.uiState.value.events.size)
        assertEquals(AcademicEventType.EXAM, vm.uiState.value.events.first().type)

        vm.setTypeFilter(null)
        vm.saveEvent(null, "Class Meeting", AcademicEventType.ACTIVITY, "2026-12-14", "2026-12-16", null)
        assertEquals(3, vm.uiState.value.events.size)
        // Setelah menyimpan agenda baru, kalender otomatis mengarah ke bulan agenda & menandai tanggalnya
        assertEquals(YearMonth.of(2026, 12), vm.uiState.value.displayedMonth)
        assertEquals(LocalDate.of(2026, 12, 14), vm.uiState.value.selectedDate)

        // Cek penanda titik agenda multi-hari pada grid kalender Desember 2026
        val decCells = vm.uiState.value.calendarDays(today = LocalDate.of(2026, 12, 1))
        val day15 = decCells.first { it.date == LocalDate.of(2026, 12, 15) }
        val day22 = decCells.first { it.date == LocalDate.of(2026, 12, 22) }
        assertTrue(AcademicEventType.ACTIVITY in day15.eventTypes)
        assertTrue(AcademicEventType.HOLIDAY in day22.eventTypes)

        // Ketuk tanggal 14 lagi membatalkan seleksi tanggal dan menampilkan seluruh agenda bulan Desember (2 agenda)
        vm.selectDate(LocalDate.of(2026, 12, 14))
        assertNull(vm.uiState.value.selectedDate)
        assertEquals(2, vm.uiState.value.agendaEvents.size)

        // Ketuk tanggal 22 memfilter agenda hanya untuk tanggal 22 Desember
        vm.selectDate(LocalDate.of(2026, 12, 22))
        assertEquals(listOf("Libur Natal"), vm.uiState.value.agendaEvents.map { it.title })

        // Pindah ke Januari 2027 masih menandai rentang Libur Natal (2026-12-21..2027-01-02)
        vm.nextMonth()
        assertEquals(YearMonth.of(2027, 1), vm.uiState.value.displayedMonth)
        assertEquals(listOf("Libur Natal"), vm.uiState.value.agendaEvents.map { it.title })

        vm.toggleShowAllMonths()
        assertEquals(3, vm.uiState.value.agendaEvents.size)

        vm.deleteEvent(vm.uiState.value.events.last().id)
        assertEquals(2, vm.uiState.value.events.size)

        val studentVm = AcademicCalendarViewModel(FakeCalendarRepository(role = "STUDENT"))
        assertFalse(studentVm.uiState.value.isAdmin)
    }

    private class FakeAnnouncementRepository(
        private val role: String,
        private val homeroom: List<NamedId> = emptyList(),
        private val available: List<NamedId> = homeroom
    ) : AnnouncementRepository {
        val items = mutableListOf(
            Announcement(1, "Info Sekolah", "Upacara Senin", "SCHOOL", null, null, "Admin", "2026-11-01"),
            Announcement(2, "Info Kelas X-1", "Bawa buku gambar", "CLASS", 5, "X-1", "Bu Maria", "2026-11-02")
        )
        val uploadedUris = mutableListOf<String>()
        var uploadError: String? = null
        var isFromCache = false

        override suspend fun currentRole() = role
        override suspend fun homeroomClasses() = homeroom
        override suspend fun availableClasses() = available

        override suspend fun getAnnouncements(
            scope: String?,
            classGroupId: Int?,
            studentId: Int?,
            search: String?
        ): Resource<AnnouncementFeed> {
            val filtered = items.filter { item ->
                (scope == null || item.scope == scope) &&
                    (classGroupId == null || item.classGroupId == classGroupId)
            }
            return Resource.Success(AnnouncementFeed(filtered.toList(), isFromCache))
        }

        override suspend fun attachmentName(uri: String) = uri.substringAfterLast('/')

        override suspend fun uploadAttachment(uri: String): Resource<UploadedAttachment> {
            uploadedUris.add(uri)
            uploadError?.let { return Resource.Error(it) }
            return Resource.Success(
                UploadedAttachment(
                    url = "https://res.cloudinary.com/demo/lampiran.png",
                    publicId = "sim/announcements/lampiran",
                    fileName = "lampiran.png"
                )
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
        ): Resource<Announcement> {
            val created = Announcement(
                id = items.maxOf { it.id } + 1,
                title = title,
                content = content,
                scope = scope,
                classGroupId = classGroupId,
                className = classGroupId?.let { cid -> available.firstOrNull { it.id == cid }?.name ?: "X-1" },
                authorName = role,
                date = date ?: "2026-11-18",
                attachmentUrl = attachmentUrl,
                attachmentPublicId = attachmentPublicId
            )
            items.add(created)
            return Resource.Success(created)
        }

        override suspend fun updateAnnouncement(
            id: Int,
            title: String,
            content: String,
            date: String,
            attachmentUrl: String?,
            attachmentPublicId: String?,
            clearAttachment: Boolean
        ) = Resource.Success(items.first())

        override suspend fun deleteAnnouncement(id: Int): Resource<Unit> {
            items.removeAll { it.id == id }
            return Resource.Success(Unit)
        }
    }

    private class FakeCalendarRepository(private val role: String) : CalendarRepository {
        private val events = mutableListOf(
            AcademicEvent(1, "PTS Ganjil", AcademicEventType.EXAM, "2026-09-21", "2026-09-26", null),
            AcademicEvent(2, "Libur Natal", AcademicEventType.HOLIDAY, "2026-12-21", "2027-01-02", null)
        )

        override suspend fun currentRole() = role

        override suspend fun getEvents(type: String?, search: String?): Resource<List<AcademicEvent>> =
            Resource.Success(events.filter { type == null || it.type.wire == type })

        override suspend fun createEvent(
            title: String,
            type: AcademicEventType,
            startDate: String,
            endDate: String,
            description: String?
        ): Resource<AcademicEvent> {
            val created = AcademicEvent(events.maxOf { it.id } + 1, title, type, startDate, endDate, description)
            events.add(created)
            return Resource.Success(created)
        }

        override suspend fun updateEvent(
            id: Int,
            title: String,
            type: AcademicEventType,
            startDate: String,
            endDate: String,
            description: String?
        ) = Resource.Success(events.first())

        override suspend fun deleteEvent(id: Int): Resource<Unit> {
            events.removeAll { it.id == id }
            return Resource.Success(Unit)
        }
    }
}
