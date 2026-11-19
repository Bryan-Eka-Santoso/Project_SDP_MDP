package id.sch.santaagnes.sim.data.local

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sch.santaagnes.sim.core.CalendarEntry
import id.sch.santaagnes.sim.core.DateTimeWib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Akses tulis ke kalender perangkat lewat CalendarContract (ADR-009). */
interface DeviceCalendar {
    fun hasPermission(): Boolean

    /** Id kalender utama yang boleh ditulis, atau null bila perangkat tidak punya. */
    suspend fun findWritableCalendarId(): Long?

    suspend fun insert(calendarId: Long, entry: CalendarEntry): Long?

    /** False bila event sudah tidak ada (mis. dihapus pengguna dari aplikasi Kalender). */
    suspend fun update(eventId: Long, entry: CalendarEntry): Boolean

    suspend fun delete(eventId: Long)
}

@Singleton
class ContentResolverDeviceCalendar @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DeviceCalendar {

    private val resolver get() = context.contentResolver

    override fun hasPermission(): Boolean = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    override suspend fun findWritableCalendarId(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY
            )
            val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND " +
                "${CalendarContract.Calendars.VISIBLE} = 1"
            val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
            data class Candidate(val id: Long, val score: Int)
            val candidates = mutableListOf<Candidate>()
            resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    var score = 0
                    if (c.getInt(2) == 1) score += 2
                    if (c.getString(1) == GOOGLE_ACCOUNT_TYPE) score += 1
                    candidates += Candidate(c.getLong(0), score)
                }
            }
            candidates.maxByOrNull { it.score }?.id
        }.getOrNull()
    }

    override suspend fun insert(calendarId: Long, entry: CalendarEntry): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val values = entry.toValues().apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) }
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
        }.getOrNull()
    }

    override suspend fun update(eventId: Long, entry: CalendarEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            resolver.update(uri, entry.toValues(), null, null) > 0
        }.getOrDefault(false)
    }

    override suspend fun delete(eventId: Long) {
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
            }
        }
    }

    private fun CalendarEntry.toValues() = ContentValues().apply {
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DESCRIPTION, description)
        put(CalendarContract.Events.EVENT_LOCATION, location)
        put(CalendarContract.Events.DTSTART, startMillis)
        put(CalendarContract.Events.DTEND, endMillis)
        put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else DateTimeWib.zone.id)
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
