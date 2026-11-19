package id.sch.santaagnes.sim.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sch.santaagnes.sim.core.SyncedRef
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.calendarSyncDataStore by preferencesDataStore(name = "calendar_sync")

/** Status sinkron kalender per akun, supaya akun lain di perangkat yang sama tidak ikut terpengaruh. */
interface CalendarSyncStore {
    suspend fun isEnabled(userId: Int): Boolean
    suspend fun setEnabled(userId: Int, enabled: Boolean)
    suspend fun calendarId(userId: Int): Long?
    suspend fun refs(userId: Int): Map<String, SyncedRef>
    suspend fun lastSyncMillis(userId: Int): Long?
    suspend fun saveSync(userId: Int, calendarId: Long, refs: Map<String, SyncedRef>, syncedAtMillis: Long)
    suspend fun clearRefs(userId: Int)
}

@Singleton
class DataStoreCalendarSyncStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CalendarSyncStore {

    private val gson = Gson()
    private val refsType = object : TypeToken<Map<String, SyncedRef>>() {}.type

    private fun enabledKey(userId: Int) = booleanPreferencesKey("enabled_$userId")
    private fun calendarKey(userId: Int) = longPreferencesKey("calendar_$userId")
    private fun refsKey(userId: Int) = stringPreferencesKey("refs_$userId")
    private fun lastSyncKey(userId: Int) = longPreferencesKey("last_sync_$userId")

    private suspend fun snapshot() = context.calendarSyncDataStore.data.first()

    override suspend fun isEnabled(userId: Int): Boolean = snapshot()[enabledKey(userId)] ?: false

    override suspend fun setEnabled(userId: Int, enabled: Boolean) {
        context.calendarSyncDataStore.edit { it[enabledKey(userId)] = enabled }
    }

    override suspend fun calendarId(userId: Int): Long? = snapshot()[calendarKey(userId)]

    override suspend fun refs(userId: Int): Map<String, SyncedRef> {
        val json = snapshot()[refsKey(userId)] ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, SyncedRef>>(json, refsType) }.getOrNull() ?: emptyMap()
    }

    override suspend fun lastSyncMillis(userId: Int): Long? = snapshot()[lastSyncKey(userId)]

    override suspend fun saveSync(
        userId: Int,
        calendarId: Long,
        refs: Map<String, SyncedRef>,
        syncedAtMillis: Long
    ) {
        context.calendarSyncDataStore.edit {
            it[calendarKey(userId)] = calendarId
            it[refsKey(userId)] = gson.toJson(refs)
            it[lastSyncKey(userId)] = syncedAtMillis
        }
    }

    override suspend fun clearRefs(userId: Int) {
        context.calendarSyncDataStore.edit {
            it.remove(calendarKey(userId))
            it.remove(refsKey(userId))
            it.remove(lastSyncKey(userId))
        }
    }
}
