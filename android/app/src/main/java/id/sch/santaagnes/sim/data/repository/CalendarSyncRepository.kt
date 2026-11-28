package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.CalendarEntry
import id.sch.santaagnes.sim.core.CalendarSyncPlanner
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.core.SyncedRef
import id.sch.santaagnes.sim.data.local.CalendarSyncStore
import id.sch.santaagnes.sim.data.local.DeviceCalendar
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.data.remote.RiwayatPeminjamanApi
import id.sch.santaagnes.sim.domain.model.CalendarSyncResult
import id.sch.santaagnes.sim.domain.model.DeviceCalendarAccount
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sinkron agenda kalender akademik (semua role) dan peminjaman yang disetujui (siswa dan guru
 * peminjam) ke Google Calendar / kalender perangkat, F-JDW-06. Jadwal pelajaran tidak ikut (ADR-009).
 */
interface CalendarSyncRepository {
    fun hasCalendarPermission(): Boolean

    suspend fun isEnabled(userId: Int): Boolean

    suspend fun lastSyncMillis(userId: Int): Long?

    /** Ringkasan sinkron terakhir, termasuk yang berjalan otomatis; null bila belum pernah. */
    suspend fun lastResult(userId: Int): CalendarSyncResult? = null

    suspend fun selectedCalendarId(userId: Int): Long?

    suspend fun activeCalendarLabel(userId: Int): String?

    suspend fun availableCalendars(): List<DeviceCalendarAccount>

    /** Menyalakan sinkron dan langsung menulis agenda; gagal berarti sinkron tetap mati. */
    suspend fun enable(userId: Int, role: String): Resource<CalendarSyncResult>

    /** Mematikan sinkron dan menghapus semua agenda yang pernah ditulis aplikasi. */
    suspend fun disable(userId: Int)

    /** Memaksa sinkronisasi segera (tombol "Sinkronkan Sekarang" atau saat admin mengubah agenda). */
    suspend fun syncNow(userId: Int, role: String): Resource<CalendarSyncResult>

    /** Memilih akun kalender tujuan (mis. memilih akun Google Calendar tertentu) lalu langsung menyinkronkan. */
    suspend fun selectCalendar(userId: Int, role: String, calendarId: Long): Resource<CalendarSyncResult>

    /**
     * Sinkron ulang saat aplikasi dibuka; null bila mati, izin dicabut, atau baru saja disinkron.
     * [force] melewati batas 10 menit, dipakai saat push peminjaman masuk.
     */
    suspend fun syncIfEnabled(userId: Int, role: String, force: Boolean = false): Resource<CalendarSyncResult>?

    /** Dipakai saat keluar akun: agenda dibuang dari perangkat, pengaturan sinkron tetap tersimpan. */
    suspend fun clearDeviceEvents(userId: Int)
}

@Singleton
class CalendarSyncRepositoryImpl internal constructor(
    private val calendarRepository: CalendarRepository,
    private val loadBookings: suspend () -> Resource<List<PeminjamanDto>>,
    private val store: CalendarSyncStore,
    private val device: DeviceCalendar,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : CalendarSyncRepository {

    @Inject
    constructor(
        calendarRepository: CalendarRepository,
        bookingApi: RiwayatPeminjamanApi,
        store: CalendarSyncStore,
        device: DeviceCalendar,
        errors: ApiErrorMapper
    ) : this(
        calendarRepository = calendarRepository,
        loadBookings = { errors.call { bookingApi.getRiwayat(limit = BOOKING_LIMIT).data.orEmpty() } },
        store = store,
        device = device
    )

    override fun hasCalendarPermission(): Boolean = device.hasPermission()

    override suspend fun isEnabled(userId: Int): Boolean = store.isEnabled(userId)

    override suspend fun lastSyncMillis(userId: Int): Long? = store.lastSyncMillis(userId)

    override suspend fun lastResult(userId: Int): CalendarSyncResult? = store.lastResult(userId)

    override suspend fun selectedCalendarId(userId: Int): Long? =
        store.selectedCalendarId(userId) ?: store.calendarId(userId)

    override suspend fun activeCalendarLabel(userId: Int): String? {
        val saved = store.calendarLabel(userId)
        if (!saved.isNullOrBlank()) return saved
        if (!device.hasPermission()) return null
        val calendars = device.listWritableCalendars()
        val chosen = selectedCalendarId(userId)?.let { id -> calendars.firstOrNull { it.id == id } }
            ?: calendars.firstOrNull()
        return chosen?.shortLabel
    }

    override suspend fun availableCalendars(): List<DeviceCalendarAccount> = device.listWritableCalendars()

    override suspend fun enable(userId: Int, role: String): Resource<CalendarSyncResult> {
        val result = sync(userId, role)
        if (result is Resource.Success) store.setEnabled(userId, true)
        return result
    }

    override suspend fun disable(userId: Int) {
        clearDeviceEvents(userId)
        store.setEnabled(userId, false)
    }

    override suspend fun syncNow(userId: Int, role: String): Resource<CalendarSyncResult> {
        val result = sync(userId, role)
        if (result is Resource.Success && !store.isEnabled(userId)) {
            store.setEnabled(userId, true)
        }
        return result
    }

    override suspend fun selectCalendar(
        userId: Int,
        role: String,
        calendarId: Long
    ): Resource<CalendarSyncResult> {
        store.setSelectedCalendarId(userId, calendarId)
        val result = sync(userId, role)
        if (result is Resource.Success) store.setEnabled(userId, true)
        return result
    }

    override suspend fun syncIfEnabled(userId: Int, role: String, force: Boolean): Resource<CalendarSyncResult>? {
        if (!store.isEnabled(userId) || !device.hasPermission()) return null
        val last = store.lastSyncMillis(userId)
        if (!force && last != null && nowMillis() - last < MIN_INTERVAL_MILLIS) return null
        return sync(userId, role)
    }

    override suspend fun clearDeviceEvents(userId: Int) {
        if (device.hasPermission()) {
            store.refs(userId).values.forEach { device.delete(it.eventId) }
        }
        store.clearRefs(userId)
    }

    private suspend fun sync(userId: Int, role: String): Resource<CalendarSyncResult> {
        if (!device.hasPermission()) return Resource.Error("Izin akses kalender belum diberikan.")
        val calendars = device.listWritableCalendars()
        val preferredId = store.selectedCalendarId(userId)
        val targetCalendar = preferredId?.let { id -> calendars.firstOrNull { it.id == id } }
            ?: calendars.firstOrNull()
            ?: return Resource.Error(
                "Tidak ditemukan akun Google Calendar yang bisa diisi di perangkat. Pastikan Anda sudah masuk ke aplikasi Google Calendar."
            )
        val calendarId = targetCalendar.id
        val calendarLabel = targetCalendar.shortLabel

        // Gagal memuat salah satu sumber membatalkan sinkron, agar agenda lama tidak ikut terhapus.
        val desired = mutableListOf<CalendarEntry>()
        when (val events = calendarRepository.getEvents()) {
            is Resource.Success -> desired += events.data.mapNotNull(CalendarEntries::fromAcademicEvent)
            is Resource.Error -> return events
        }
        if (role == ROLE_STUDENT || role == ROLE_TEACHER) {
            when (val bookings = loadBookings()) {
                is Resource.Success -> desired += bookings.data.mapNotNull(CalendarEntries::fromBooking)
                is Resource.Error -> return bookings
            }
        }

        var refs = store.refs(userId)
        val storedCalendarId = store.calendarId(userId)
        if (storedCalendarId != null && storedCalendarId != calendarId) {
            // Akun Google Calendar tujuan berganti: bersihkan event lama di akun sebelumnya lalu tulis ke akun baru.
            refs.values.forEach { device.delete(it.eventId) }
            refs = emptyMap()
        }

        // Event yang dihapus pengguna di kalender HP dianggap belum pernah ditulis, sehingga dibuat lagi.
        val existing = device.existingEventIds(refs.values.map { it.eventId })
        refs = refs.filterValues { it.eventId in existing }

        val plan = CalendarSyncPlanner.plan(desired, refs)
        val newRefs = refs.toMutableMap()

        for ((key, ref) in plan.deletes) {
            device.delete(ref.eventId)
            newRefs.remove(key)
        }
        var updated = 0
        var added = 0
        for ((entry, ref) in plan.updates) {
            if (device.update(ref.eventId, entry)) {
                newRefs[entry.key] = SyncedRef(ref.eventId, entry.fingerprint)
                updated += 1
            } else {
                // Event sudah dihapus pengguna dari aplikasi Google Calendar: tulis ulang.
                device.insert(calendarId, entry)?.let {
                    newRefs[entry.key] = SyncedRef(it, entry.fingerprint)
                    added += 1
                } ?: newRefs.remove(entry.key)
            }
        }
        for (entry in plan.inserts) {
            device.insert(calendarId, entry)?.let {
                newRefs[entry.key] = SyncedRef(it, entry.fingerprint)
                added += 1
            }
        }

        store.setSelectedCalendarId(userId, calendarId)
        store.saveSync(userId, calendarId, newRefs, nowMillis(), calendarLabel)
        val result = CalendarSyncResult(
            added = added,
            updated = updated,
            removed = plan.deletes.size,
            total = newRefs.size,
            accountLabel = calendarLabel
        )
        store.saveLastResult(userId, result)
        return Resource.Success(result)
    }

    private companion object {
        const val ROLE_STUDENT = "STUDENT"
        const val ROLE_TEACHER = "TEACHER"
        const val BOOKING_LIMIT = 100
        const val MIN_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}
