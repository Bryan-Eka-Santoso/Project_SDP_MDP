package id.sch.santaagnes.sim.core

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.ApiErrorBody
import id.sch.santaagnes.sim.data.remote.ApiErrorDetail
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private class ErrorEnvelope(val error: ApiErrorBody?)

/** Membaca `error` dari badan respons backend; null bila badan kosong atau bukan JSON amplop. */
fun parseApiError(body: String?): ApiErrorBody? {
    if (body.isNullOrBlank()) return null
    return try {
        Gson().fromJson(body, ErrorEnvelope::class.java)?.error
    } catch (_: Exception) {
        null
    }
}

/**
 * Mengubah kegagalan jaringan/HTTP menjadi pesan bahasa Indonesia. Pesan dari server dipakai bila
 * ada (backend menjamin aman ditampilkan); selain itu pesan bawaan dari `strings.xml`.
 */
@Singleton
class ApiErrorMapper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend fun <T> call(action: suspend () -> T): Resource<T> =
        try {
            Resource.Success(action())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Resource.Error(message(exception))
        }

    fun message(error: Throwable): String = when (error) {
        is IOException -> context.getString(R.string.error_network)
        is HttpException -> {
            val body = parseApiError(error.response()?.errorBody()?.string())
            val reasons = body?.details.orEmpty().mapNotNull(::describe).distinct()
            // Kegagalan validasi, bentrok jadwal, dan impor: alasan per field/baris lebih berguna
            // daripada kalimat umum di `message`.
            val fromServer = if (body?.code in DETAIL_CODES && reasons.isNotEmpty()) {
                reasons.joinToString("\n")
            } else {
                body?.message
            }
            if (!fromServer.isNullOrBlank()) fromServer else byStatus(error.code())
        }
        else -> context.getString(R.string.error_unknown)
    }

    private fun describe(detail: ApiErrorDetail): String? {
        val message = detail.message?.takeIf { it.isNotBlank() } ?: return null
        val row = detail.row ?: return message
        val column = detail.column?.let { ", kolom $it" }.orEmpty()
        return "Baris $row$column: $message"
    }

    private fun byStatus(code: Int): String = context.getString(
        when (code) {
            400 -> R.string.error_bad_request
            401 -> R.string.error_unauthorized
            403 -> R.string.error_forbidden
            404 -> R.string.error_not_found
            429 -> R.string.error_too_many
            else -> R.string.error_server
        }
    )

    private companion object {
        val DETAIL_CODES = setOf("VALIDATION_ERROR", "SCHEDULE_CONFLICT", "IMPORT_INVALID")
    }
}
