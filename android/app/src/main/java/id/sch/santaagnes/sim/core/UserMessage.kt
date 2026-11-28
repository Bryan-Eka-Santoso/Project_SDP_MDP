package id.sch.santaagnes.sim.core

import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException

const val MESSAGE_NO_CONNECTION = "Tidak dapat terhubung ke server. Periksa koneksi internet Anda."
const val MESSAGE_SERVER = "Terjadi kesalahan pada server. Silakan coba lagi."

private val REASON_CODES = setOf("VALIDATION_ERROR", "SCHEDULE_CONFLICT", "IMPORT_INVALID")

/** Pesan server (atau rincian validasinya). Badan error HTTP hanya bisa dibaca sekali. */
fun HttpException.serverMessage(): String? {
    val body = parseApiError(response()?.errorBody()?.string()) ?: return null
    val reasons = body.details.orEmpty().mapNotNull { it.message?.takeIf(String::isNotBlank) }.distinct()
    return if (body.code in REASON_CODES && reasons.isNotEmpty()) {
        reasons.joinToString("\n")
    } else {
        body.message?.takeIf(String::isNotBlank)
    }
}

/**
 * Untuk ViewModel yang memanggil API tanpa [ApiErrorMapper]: pengguna tidak boleh melihat teks
 * teknis seperti "HTTP 409" atau "Failed to connect to /alamat".
 */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is UploadRejectedException -> message
    is IOException -> MESSAGE_NO_CONNECTION
    is HttpException -> serverMessage() ?: if (code() >= 500) MESSAGE_SERVER else fallback
    is JsonParseException -> fallback
    else -> message?.takeIf(String::isNotBlank) ?: fallback
}
