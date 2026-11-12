package id.sch.santaagnes.sim.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Membaca berkas pilihan pengguna (Storage Access Framework) untuk diunggah sebagai multipart. */
@Singleton
class UploadFiles @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Nama berkas dari pemilih file (uri teks), atau null bila tidak terbaca. */
    suspend fun name(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    /** Bagian multipart `file` berisi berkas Excel; [IOException] bila berkas tidak dapat dibaca. */
    suspend fun xlsxPart(uri: String, fallbackName: String): MultipartBody.Part {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        } ?: throw IOException("Berkas tidak dapat dibaca.")
        // Server memeriksa tipe dari isi berkas, bukan header, jadi tipe umum Excel sudah cukup.
        val body = bytes.toRequestBody(XLSX.toMediaType())
        return MultipartBody.Part.createFormData("file", name(uri) ?: fallbackName, body)
    }

    private companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
