
package id.sch.santaagnes.sim.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import id.sch.santaagnes.sim.domain.model.NamedId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "session"
)

class SessionManager(
    private val context: Context
) {
    companion object {
        private val ACCESS_TOKEN =
            stringPreferencesKey("access_token")

        private val REFRESH_TOKEN =
            stringPreferencesKey("refresh_token")

        private val ACTIVE_STUDENT_ID =
            stringPreferencesKey("active_student_id")

        private val ROLE = stringPreferencesKey("role")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_ID = intPreferencesKey("user_id")
        private val STUDENT_ID = intPreferencesKey("student_id")
        private val IS_HOMEROOM_TEACHER = booleanPreferencesKey("is_homeroom_teacher")
        private val HOMEROOM_GROUPS = stringPreferencesKey("homeroom_groups")
        private val CHILDREN = stringPreferencesKey("children")
    }

    private val gson = Gson()

    val accessToken = context.dataStore.data.map {
        it[ACCESS_TOKEN]
    }

    suspend fun getAccessToken(): String? {
        return accessToken.first()
    }

    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.first()[REFRESH_TOKEN]
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String
    ) {
        context.dataStore.edit {
            it[ACCESS_TOKEN] = accessToken
            it[REFRESH_TOKEN] = refreshToken
        }
    }

    /** Data pengguna yang dibutuhkan saat aplikasi dibuka ulang (auto-login) tanpa memanggil server. */
    suspend fun saveUser(
        userId: Int,
        name: String,
        role: String,
        isHomeroomTeacher: Boolean,
        studentId: Int?,
        homeroomClassGroups: List<NamedId> = emptyList(),
        children: List<NamedId> = emptyList()
    ) {
        context.dataStore.edit {
            it[USER_ID] = userId
            it[USER_NAME] = name
            it[ROLE] = role
            it[IS_HOMEROOM_TEACHER] = isHomeroomTeacher
            if (studentId != null) it[STUDENT_ID] = studentId else it.remove(STUDENT_ID)
            it[HOMEROOM_GROUPS] = gson.toJson(homeroomClassGroups)
            it[CHILDREN] = gson.toJson(children)
        }
    }

    suspend fun getRole(): String? = context.dataStore.data.first()[ROLE]

    suspend fun getUserName(): String? = context.dataStore.data.first()[USER_NAME]

    suspend fun getHomeroomClassGroups(): List<NamedId> = readList(HOMEROOM_GROUPS)

    suspend fun getChildren(): List<NamedId> = readList(CHILDREN)

    private suspend fun readList(key: Preferences.Key<String>): List<NamedId> {
        val json = context.dataStore.data.first()[key] ?: return emptyList()
        return try {
            gson.fromJson(json, Array<NamedId>::class.java).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getUserId(): Int? = context.dataStore.data.first()[USER_ID]

    /** id siswa milik akun STUDENT; beda dengan [getActiveStudentId] (anak yang dipilih wali murid). */
    suspend fun getStudentId(): Int? = context.dataStore.data.first()[STUDENT_ID]

    /** Sesi dianggap ada bila token dan role tersimpan. */
    suspend fun hasSession(): Boolean {
        val preferences = context.dataStore.data.first()
        return !preferences[ACCESS_TOKEN].isNullOrBlank() && preferences[ROLE] != null
    }

    suspend fun saveActiveStudentId(studentId: Int) {
        context.dataStore.edit {
            it[ACTIVE_STUDENT_ID] = studentId.toString()
        }
    }

    suspend fun getActiveStudentId(): Int? {
        val preferences = context.dataStore.data.first()
        return preferences[ACTIVE_STUDENT_ID]?.toIntOrNull()
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.clear()
        }
    }
}
