package id.sch.santaagnes.sim.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(
    name = "user_session"
)

@Singleton
class SessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private val ACCESS_TOKEN =
            stringPreferencesKey("access_token")

        private val REFRESH_TOKEN =
            stringPreferencesKey("refresh_token")

        private val ACTIVE_STUDENT_ID =
            stringPreferencesKey("active_student_id")
    }

    val accessToken: Flow<String?> =
        context.sessionDataStore.data.map { preferences ->
            preferences[ACCESS_TOKEN]
        }

    val refreshToken: Flow<String?> =
        context.sessionDataStore.data.map { preferences ->
            preferences[REFRESH_TOKEN]
        }

    val activeStudentId: Flow<String?> =
        context.sessionDataStore.data.map { preferences ->
            preferences[ACTIVE_STUDENT_ID]
        }

    suspend fun getAccessToken(): String? {
        val preferences = context.sessionDataStore.data.first()
        return preferences[ACCESS_TOKEN]
    }

    suspend fun getRefreshToken(): String? {
        val preferences = context.sessionDataStore.data.first()
        return preferences[REFRESH_TOKEN]
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String
    ) {
        context.sessionDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = accessToken
            preferences[REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun saveActiveStudentId(studentId: Int) {
        context.sessionDataStore.edit { preferences ->
            preferences[ACTIVE_STUDENT_ID] = studentId.toString()
        }
    }

    suspend fun saveActiveStudent(studentId: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[ACTIVE_STUDENT_ID] = studentId
        }
    }

    suspend fun getActiveStudentId(): Int? {
        val preferences = context.sessionDataStore.data.first()
        return preferences[ACTIVE_STUDENT_ID]?.toIntOrNull()
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
