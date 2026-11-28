package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.domain.model.CalendarSyncResult
import id.sch.santaagnes.sim.core.CalendarEntry
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.core.SyncedRef
import id.sch.santaagnes.sim.data.local.CalendarSyncStore
import id.sch.santaagnes.sim.data.local.DeviceCalendar
import id.sch.santaagnes.sim.data.local.sortWritableCalendars
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.data.repository.CalendarRepository
import id.sch.santaagnes.sim.data.repository.CalendarSyncRepositoryImpl
import id.sch.santaagnes.sim.domain.model.AcademicEvent
import id.sch.santaagnes.sim.domain.model.AcademicEventType
import id.sch.santaagnes.sim.domain.model.DeviceCalendarAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncRepositoryTest {

    private class FakeCalendarRepository(
        var eventsResult: Resource<List<AcademicEvent>> = Resource.Success(emptyList())
    ) : CalendarRepository {
        override suspend fun currentRole(): String? = "STUDENT"
        override suspend fun getEvents(type: String?, search: String?): Resource<List<AcademicEvent>> = eventsResult
        override suspend fun createEvent(
            title: String,
            type: AcademicEventType,
            startDate: String,
            endDate: String,
            description: String?
        ): Resource<AcademicEvent> = Resource.Error("unused")

        override suspend fun updateEvent(
            id: Int,
            title: String,
            type: AcademicEventType,
            startDate: String,
            endDate: String,
            description: String?
        ): Resource<AcademicEvent> = Resource.Error("unused")

        override suspend fun deleteEvent(id: Int): Resource<Unit> = Resource.Error("unused")
    }

    private class FakeCalendarSyncStore : CalendarSyncStore {
        private val enabled = mutableMapOf<Int, Boolean>()
        private val calendarIds = mutableMapOf<Int, Long>()
        private val selectedCalendarIds = mutableMapOf<Int, Long>()
        private val calendarLabels = mutableMapOf<Int, String>()
        private val refMap = mutableMapOf<Int, Map<String, SyncedRef>>()
        private val lastSyncs = mutableMapOf<Int, Long>()
        private val lastResults = mutableMapOf<Int, CalendarSyncResult>()

        override suspend fun lastResult(userId: Int): CalendarSyncResult? = lastResults[userId]
        override suspend fun saveLastResult(userId: Int, result: CalendarSyncResult) {
            lastResults[userId] = result
        }

        override suspend fun isEnabled(userId: Int): Boolean = enabled[userId] ?: false
        override suspend fun setEnabled(userId: Int, enabled: Boolean) {
            this.enabled[userId] = enabled
        }

        override suspend fun calendarId(userId: Int): Long? = calendarIds[userId]
        override suspend fun selectedCalendarId(userId: Int): Long? = selectedCalendarIds[userId]
        override suspend fun setSelectedCalendarId(userId: Int, calendarId: Long?) {
            if (calendarId == null) selectedCalendarIds.remove(userId) else selectedCalendarIds[userId] = calendarId
        }

        override suspend fun calendarLabel(userId: Int): String? = calendarLabels[userId]
        override suspend fun refs(userId: Int): Map<String, SyncedRef> = refMap[userId].orEmpty()
        override suspend fun lastSyncMillis(userId: Int): Long? = lastSyncs[userId]

        override suspend fun saveSync(
            userId: Int,
            calendarId: Long,
            refs: Map<String, SyncedRef>,
            syncedAtMillis: Long,
            calendarLabel: String?
        ) {
            calendarIds[userId] = calendarId
            refMap[userId] = refs
            lastSyncs[userId] = syncedAtMillis
            if (calendarLabel != null) calendarLabels[userId] = calendarLabel
        }

        override suspend fun clearRefs(userId: Int) {
            calendarIds.remove(userId)
            calendarLabels.remove(userId)
            refMap.remove(userId)
            lastSyncs.remove(userId)
            lastResults.remove(userId)
        }
    }

    private class FakeDeviceCalendar(
        var permissionGranted: Boolean = true,
        var calendars: List<DeviceCalendarAccount> = emptyList()
    ) : DeviceCalendar {
        private var nextEventId = 100L
        val inserted = mutableMapOf<Long, Pair<Long, CalendarEntry>>() // eventId -> (calendarId, entry)
        val deletedIds = mutableListOf<Long>()

        override fun hasPermission(): Boolean = permissionGranted
        override suspend fun listWritableCalendars(): List<DeviceCalendarAccount> =
            if (permissionGranted) sortWritableCalendars(calendars) else emptyList()

        override suspend fun findWritableCalendarId(): Long? = listWritableCalendars().firstOrNull()?.id

        override suspend fun insert(calendarId: Long, entry: CalendarEntry): Long {
            val id = nextEventId++
            inserted[id] = calendarId to entry
            return id
        }

        override suspend fun existingEventIds(ids: Collection<Long>): Set<Long> =
            ids.filter { it in inserted }.toSet()

        override suspend fun update(eventId: Long, entry: CalendarEntry): Boolean {
            val existing = inserted[eventId] ?: return false
            inserted[eventId] = existing.first to entry
            return true
        }

        override suspend fun delete(eventId: Long) {
            deletedIds += eventId
            inserted.remove(eventId)
        }
    }

    private val googlePrimary = DeviceCalendarAccount(
        id = 10L,
        displayName = "siswa@gmail.com",
        accountName = "siswa@gmail.com",
        accountType = DeviceCalendarAccount.GOOGLE_ACCOUNT_TYPE,
        isPrimary = true
    )
    private val googleSecondary = DeviceCalendarAccount(
        id = 11L,
        displayName = "Agenda Sekolah",
        accountName = "sekolah@gmail.com",
        accountType = DeviceCalendarAccount.GOOGLE_ACCOUNT_TYPE,
        isPrimary = false
    )
    private val localCalendar = DeviceCalendarAccount(
        id = 20L,
        displayName = "Kalender Lokal",
        accountName = "local",
        accountType = "LOCAL",
        isPrimary = true
    )

    private fun academicEvent(id: Int = 1, title: String = "UTS Ganjil") = AcademicEvent(
        id = id,
        title = title,
        type = AcademicEventType.EXAM,
        startDate = "2026-12-01",
        endDate = "2026-12-05",
        description = "Ujian tengah semester"
    )

    private fun booking(id: Int = 5, status: String = "APPROVED") = PeminjamanDto(
        id = id,
        facilityId = 3,
        facilityName = "Lab Komputer",
        facilityType = "ROOM",
        requesterId = 1,
        requesterName = "Siswa Satu",
        bookingDate = "2026-12-02",
        startTime = "09:00",
        endTime = "11:00",
        purpose = "Praktikum",
        status = status,
        verifierName = "Administrator"
    )

    @Test
    fun `prioritas kalender menempatkan Google Calendar utama di posisi pertama`() {
        val sorted = sortWritableCalendars(listOf(localCalendar, googleSecondary, googlePrimary))

        assertEquals(listOf(10L, 11L, 20L), sorted.map { it.id })
        assertTrue(sorted.first().isGoogle)
        assertEquals("siswa@gmail.com", sorted.first().shortLabel)
        assertEquals("Kalender Lokal", localCalendar.shortLabel)
    }

    @Test
    fun `enable menyinkronkan agenda akademik dan peminjaman disetujui ke akun Google utama`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent())))
        val store = FakeCalendarSyncStore()
        val device = FakeDeviceCalendar(calendars = listOf(localCalendar, googlePrimary))
        var clock = 1_000_000L
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { Resource.Success(listOf(booking(5, "APPROVED"), booking(6, "PENDING"))) },
            store = store,
            device = device,
            nowMillis = { clock }
        )

        val result = repo.enable(userId = 1, role = "STUDENT")

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(2, data.added)
        assertEquals(2, data.total)
        assertEquals("siswa@gmail.com", data.accountLabel)
        assertTrue(repo.isEnabled(1))
        assertEquals(10L, repo.selectedCalendarId(1))
        assertEquals("siswa@gmail.com", repo.activeCalendarLabel(1))
        assertTrue(device.inserted.values.all { it.first == 10L })
    }

    @Test
    fun `wali murid dan admin hanya menyinkronkan agenda akademik tanpa memuat peminjaman`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent())))
        val store = FakeCalendarSyncStore()
        val device = FakeDeviceCalendar(calendars = listOf(googlePrimary))
        var bookingCalls = 0
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = {
                bookingCalls += 1
                Resource.Success(listOf(booking()))
            },
            store = store,
            device = device
        )

        val result = repo.enable(userId = 2, role = "GUARDIAN")

        assertTrue(result is Resource.Success)
        assertEquals(1, (result as Resource.Success).data.total)
        assertEquals(0, bookingCalls)
    }

    @Test
    fun `selectCalendar memindahkan event dari akun Google lama ke akun Google baru`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent())))
        val store = FakeCalendarSyncStore()
        val device = FakeDeviceCalendar(calendars = listOf(googlePrimary, googleSecondary))
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { Resource.Success(emptyList()) },
            store = store,
            device = device
        )

        repo.enable(userId = 1, role = "STUDENT")
        val oldEventIds = device.inserted.keys.toList()
        assertEquals(1, oldEventIds.size)

        val switched = repo.selectCalendar(userId = 1, role = "STUDENT", calendarId = googleSecondary.id)

        assertTrue(switched is Resource.Success)
        val data = (switched as Resource.Success).data
        assertEquals("sekolah@gmail.com", data.accountLabel)
        assertEquals(1, data.added)
        assertEquals(1, data.total)
        assertEquals(oldEventIds, device.deletedIds)
        assertEquals(googleSecondary.id, repo.selectedCalendarId(1))
        assertTrue(device.inserted.values.all { it.first == googleSecondary.id })
    }

    @Test
    fun `syncNow melewati jeda 10 menit sementara syncIfEnabled menghormati jeda`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent(1, "Agenda Awal"))))
        val store = FakeCalendarSyncStore()
        val device = FakeDeviceCalendar(calendars = listOf(googlePrimary))
        var clock = 5_000_000L
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { Resource.Success(emptyList()) },
            store = store,
            device = device,
            nowMillis = { clock }
        )

        repo.enable(userId = 1, role = "ADMIN")

        // 1 menit kemudian: agenda berubah di server, syncIfEnabled biasa dilewati, syncNow langsung jalan.
        clock += 60_000L
        calendarRepo.eventsResult = Resource.Success(listOf(academicEvent(1, "Agenda Baru")))
        assertNull(repo.syncIfEnabled(userId = 1, role = "ADMIN", force = false))

        val forced = repo.syncNow(userId = 1, role = "ADMIN")
        assertTrue(forced is Resource.Success)
        assertEquals(1, (forced as Resource.Success).data.updated)
        assertEquals(clock, repo.lastSyncMillis(1))
        assertNotNull(device.inserted.values.firstOrNull { it.second.title.contains("Agenda Baru") })
    }

    @Test
    fun `gagal memuat peminjaman tidak menghapus event yang sudah ada di Google Calendar`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent())))
        val store = FakeCalendarSyncStore()
        val device = FakeDeviceCalendar(calendars = listOf(googlePrimary))
        var bookingsRes: Resource<List<PeminjamanDto>> = Resource.Success(listOf(booking()))
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { bookingsRes },
            store = store,
            device = device
        )

        repo.enable(userId = 1, role = "STUDENT")
        assertEquals(2, device.inserted.size)

        bookingsRes = Resource.Error("Gagal memuat peminjaman.")
        val res = repo.syncNow(userId = 1, role = "STUDENT")

        assertTrue(res is Resource.Error)
        assertEquals(2, device.inserted.size)
        assertTrue(device.deletedIds.isEmpty())
    }

    @Test
    fun `agenda yang dihapus pengguna dari kalender HP dibuat lagi saat sinkron berikutnya`() = runTest {
        val device = FakeDeviceCalendar(calendars = listOf(googlePrimary))
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = FakeCalendarRepository(Resource.Success(listOf(academicEvent()))),
            loadBookings = { Resource.Success(emptyList()) },
            store = FakeCalendarSyncStore(),
            device = device
        )
        repo.enable(userId = 1, role = "ADMIN")
        device.inserted.clear()

        val result = repo.syncNow(userId = 1, role = "ADMIN")

        assertEquals(1, (result as Resource.Success).data.added)
        assertEquals(1, device.inserted.size)
    }

    @Test
    fun `hasil sinkron otomatis tersimpan untuk ditampilkan di Profil`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent(1, "Awal"))))
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { Resource.Success(emptyList()) },
            store = FakeCalendarSyncStore(),
            device = FakeDeviceCalendar(calendars = listOf(googlePrimary))
        )
        repo.enable(userId = 1, role = "ADMIN")
        calendarRepo.eventsResult = Resource.Success(listOf(academicEvent(1, "Baru")))

        repo.syncIfEnabled(userId = 1, role = "ADMIN", force = true)

        assertEquals(1, repo.lastResult(1)?.updated)
    }

    @Test
    fun `enable gagal bila izin belum ada atau tidak ada akun kalender perangkat`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent())))
        val store = FakeCalendarSyncStore()
        val noPermDevice = FakeDeviceCalendar(permissionGranted = false, calendars = listOf(googlePrimary))
        val repoNoPerm = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { Resource.Success(emptyList()) },
            store = store,
            device = noPermDevice
        )

        val errNoPerm = repoNoPerm.enable(userId = 1, role = "STUDENT")
        assertTrue(errNoPerm is Resource.Error)
        assertFalse(repoNoPerm.isEnabled(1))

        val emptyDevice = FakeDeviceCalendar(permissionGranted = true, calendars = emptyList())
        val repoEmpty = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { Resource.Success(emptyList()) },
            store = store,
            device = emptyDevice
        )

        val errEmpty = repoEmpty.enable(userId = 1, role = "STUDENT")
        assertTrue(errEmpty is Resource.Error)
        assertTrue((errEmpty as Resource.Error).message.contains("Google Calendar"))
        assertFalse(repoEmpty.isEnabled(1))
    }

    @Test
    fun `disable menghapus seluruh event sinkron dari Google Calendar dan mematikan status`() = runTest {
        val calendarRepo = FakeCalendarRepository(Resource.Success(listOf(academicEvent())))
        val store = FakeCalendarSyncStore()
        val device = FakeDeviceCalendar(calendars = listOf(googlePrimary))
        val repo = CalendarSyncRepositoryImpl(
            calendarRepository = calendarRepo,
            loadBookings = { Resource.Success(listOf(booking())) },
            store = store,
            device = device
        )

        repo.enable(userId = 1, role = "TEACHER")
        assertEquals(2, device.inserted.size)

        repo.disable(userId = 1)

        assertFalse(repo.isEnabled(1))
        assertTrue(device.inserted.isEmpty())
        assertEquals(2, device.deletedIds.size)
    }
}
