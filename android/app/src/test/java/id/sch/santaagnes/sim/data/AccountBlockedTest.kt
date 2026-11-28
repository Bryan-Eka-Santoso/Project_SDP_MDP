package id.sch.santaagnes.sim.data

import id.sch.santaagnes.sim.data.remote.isAccountBlocked
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBlockedTest {

    private fun response(status: Int, code: String) = Response.Builder()
        .request(Request.Builder().url("http://localhost/api/v1/me/scores").build())
        .protocol(Protocol.HTTP_1_1)
        .message("x")
        .code(status)
        .body(
            """{"success":false,"error":{"code":"$code","message":"m"}}"""
                .toResponseBody("application/json".toMediaType())
        )
        .build()

    @Test
    fun blockedAccountIsDetectedAndBodyStaysReadable() {
        val blocked = response(403, "ACCOUNT_BLOCKED")

        assertTrue(isAccountBlocked(blocked))
        assertEquals(true, blocked.body?.string()?.contains("ACCOUNT_BLOCKED"))
    }

    @Test
    fun otherForbiddenResponsesDoNotEndTheSession() {
        assertFalse(isAccountBlocked(response(403, "FORBIDDEN")))
        assertFalse(isAccountBlocked(response(401, "ACCOUNT_BLOCKED")))
    }
}
