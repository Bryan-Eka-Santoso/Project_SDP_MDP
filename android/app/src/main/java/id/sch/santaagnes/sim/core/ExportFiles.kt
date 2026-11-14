package id.sch.santaagnes.sim.core

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/** Menyimpan berkas ekspor dari server (F-LAP-11) ke lokasi pilihan pengguna lewat `CreateDocument`. */
@Singleton
class ExportFiles @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** True bila isi [body] berhasil ditulis ke [destination] (uri teks dari pemilih file). */
    suspend fun write(body: ResponseBody, destination: String): Boolean = withContext(Dispatchers.IO) {
        body.use {
            runCatching {
                val output = context.contentResolver.openOutputStream(Uri.parse(destination))
                    ?: return@runCatching false
                output.use { target -> it.byteStream().copyTo(target) }
                true
            }.getOrDefault(false)
        }
    }
}
