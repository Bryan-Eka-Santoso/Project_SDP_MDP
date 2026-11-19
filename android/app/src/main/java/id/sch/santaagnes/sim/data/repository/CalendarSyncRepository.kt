package id.sch.santaagnes.sim.data.repository

import id.sch.santaagnes.sim.core.ApiErrorMapper
import id.sch.santaagnes.sim.core.CalendarEntry
import id.sch.santaagnes.sim.core.CalendarSyncPlanner
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.core.SyncedRef
import id.sch.santaagnes.sim.data.local.CalendarSyncStore
import id.sch.santaagnes.sim.data.local.DeviceCalendar
import id.sch.santaagnes.sim.data.remote.RiwayatPeminjamanApi
import id.sch.santaagnes.sim.domain.model.CalendarSyncResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sinkron agenda kalender akademik (semua role) dan peminjaman yang disetujui (siswa dan guru
 * peminjam) ke kalender perangkat, F-JDW-06. Jadwal pelajaran tidak ikut (ADR-009).
 */
interface CalendarSyncRepository {
    suspend fun isEnabled(userId: Int): Boolean

    suspend fun lastSyncMillis(userId: Int): Long?

    /** Menyalakan sinkron dan langsung menulis agenda; gagal berarti sinkron tetap mati. */
    suspend fun enable(userId: Int, role: String): Resource<CalendarSyncResult>

    /** Mematikan sinkron dan menghapus semua agenda yang pernah ditulis aplikasi. */
    suspend fun disable(userId: Int)

    /**
     * Sinkron ulang saat aplikasi dibuka; null bila mati, izin dicabut, atau baru saja disinkron.
     * [force] melewati batas 10 menit, dipakai saat push peminjaman masuk.
     */
    suspend fun syncIfEnabled(userId: Int, role: String, force: Boolean = false): Resource<CalendarSyncResult>?

    /** Dipakai saat keluar akun: agenda dibuang dari perangkat, pengaturan sinkron tetap tersimpan. */
    suspend fun clearDeviceEvents(userId: Int)
}

@Singleton
class CalendarSyncRepositoryImpl @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val bookingApi: RiwayatPeminjamanApi,
    private val store: CalendarSyncStore,
    private val device: DeviceCalendar,
    private val errors: ApiErrorMapper
) : CalendarSyncRepository {

    override suspend fun isEnabled(userId: Int): Boolean = store.isEnabled(userId)

    override suspend fun lastSyncMillis(userId: Int): Long? = store.lastSyncMillis(userId)

    override suspend fun enable(userId: Int, role: String): Resource<CalendarSyncResult> {
        val result = sync(userId, role)
        if (result is Resource.Success) store.setEnabled(userId, true)
        return result
    }

    override suspend fun disable(userId: Int) {
        clearDeviceEvents(userId)
        store.setEnabled(userId, false)
    }

    override suspend fun syncIfEnabled(userId: Int, role: String, force: Boolean): Resource<CalendarSyncResult>? {
        if (!store.isEnabled(userId) || !device.hasPermission()) return null
        val last = store.lastSyncMillis(userId)
        if (!force && last != null && System.currentTimeMillis() - last < MIN_INTERVAL_MILLIS) return null
        return sync(userId, role)
    }

    override suspend fun clearDeviceEvents(userId: Int) {
        if (device.hasPermission()) {
            store.refs(userId).values.forEach { device.delete(it.eventId) }
        }
        store.clearRefs(userId)
    }

    private suspend fun sync(userId: Int, role: String): Resource<CalendarSyncResult> {
        if (!device.hasPermission()) return Resource.Error("Izin kalender belum diberikan.")
        val calendarId = device.findWritableCalendarId()
            ?: return Resource.Error("Tidak ada kalender di perangkat yang bisa diisi. Tambahkan akun di aplikasi Kalender.")

        // Gagal memuat salah satu sumber membatalkan sinkron, agar agenda lama tidak ikut terhapus.
        val desired = mutableListOf<CalendarEntry>()
        when (val events = calendarRepository.getEvents()) {
            is Resource.Success -> desired += events.data.mapNotNull(CalendarEntries::fromAcademicEvent)
            is Resource.Error -> return events
        }
        if (role == ROLE_STUDENT || role == ROLE_TEACHER) {
            when (val bookings = errors.call { bookingApi.getRiwayat(limit = BOOKING_LIMIT).data.orEmpty() }) {
                is Resource.Success -> desired += bookings.data.mapNotNull(CalendarEntries::fromBooking)
                is Resource.Error -> return bookings
            }
        }

        var refs = store.refs(userId)
        val storedCalendarId = store.calendarId(userId)
        if (storedCalendarId != null && storedCalendarId != calendarId) {
            // Kalender tujuan berganti (mis. akun dihapus): event lama dibersihkan lalu ditulis ulang.
            refs.values.forEach { device.delete(it.eventId) }
            refs = emptyMap()
        }

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
                // Event sudah dihapus pengguna dari aplikasi Kalender: tulis ulang.
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

        store.saveSync(userId, calendarId, newRefs, System.currentTimeMillis())
        return Resource.Success(
            CalendarSyncResult(
                added = added,
                updated = updated,
                removed = plan.deletes.size,
                total = newRefs.size
            )
        )
    }

    private companion object {
        const val ROLE_STUDENT = "STUDENT"
        const val ROLE_TEACHER = "TEACHER"
        const val BOOKING_LIMIT = 100
        const val MIN_INTERVAL_MILLIS = 10 * 60 * 1000L
    }
}
