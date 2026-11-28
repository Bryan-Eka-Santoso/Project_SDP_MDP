package id.sch.santaagnes.sim.core

import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException

class UserMessageTest {

    private fun httpError(code: Int, body: String = "") = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))
    )

    private fun envelope(code: String, message: String, details: String = "") =
        """{"success":false,"error":{"code":"$code","message":"$message"$details}}"""

    @Test
    fun usesServerMessageInsteadOfHttpStatusText() {
        val error = httpError(409, envelope("SURVEY_CLOSED", "Survei sudah tidak menerima jawaban."))

        assertEquals("Survei sudah tidak menerima jawaban.", error.userMessage("Gagal menyimpan."))
    }

    @Test
    fun usesValidationDetailsWhenPresent() {
        val error = httpError(
            400,
            envelope(
                "VALIDATION_ERROR",
                "Data yang dikirim tidak valid.",
                ""","details":[{"field":"body.purpose","message":"Keperluan minimal 5 karakter."}]"""
            )
        )

        assertEquals("Keperluan minimal 5 karakter.", error.userMessage("Gagal menyimpan."))
    }

    @Test
    fun explainsByStatusWhenServerSendsNoMessage() {
        assertEquals(MESSAGE_SERVER, httpError(500).userMessage("Gagal menyimpan."))
        assertEquals("Gagal menyimpan.", httpError(409).userMessage("Gagal menyimpan."))
    }

    @Test
    fun hidesTechnicalConnectionErrors() {
        val error = ConnectException("Failed to connect to /192.0.2.10:3000")

        assertEquals(MESSAGE_NO_CONNECTION, error.userMessage("Gagal menyimpan."))
    }

    @Test
    fun keepsOurOwnIndonesianMessages() {
        val error = IllegalStateException("Semester aktif belum diatur.")

        assertEquals("Semester aktif belum diatur.", error.userMessage("Gagal memuat."))
    }

    @Test
    fun hidesParserErrors() {
        val error = JsonSyntaxException("java.lang.IllegalStateException: Expected BEGIN_OBJECT")

        assertEquals("Gagal memuat.", error.userMessage("Gagal memuat."))
    }
}
