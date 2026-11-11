
package id.sch.santaagnes.sim.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    }

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
