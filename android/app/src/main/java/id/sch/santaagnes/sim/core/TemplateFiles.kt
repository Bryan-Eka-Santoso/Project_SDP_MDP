package id.sch.santaagnes.sim.core

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menyimpan template impor Excel yang ikut terbawa di APK (`assets/`, bersumber dari
 * `docs/import-templates/`) ke lokasi pilihan pengguna lewat `CreateDocument`.
 */
@Singleton
class TemplateFiles @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** True bila template [assetName] berhasil disalin ke [destination] (uri teks dari pemilih file). */
    suspend fun save(assetName: String, destination: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(assetName).use { input ->
                val output = context.contentResolver.openOutputStream(Uri.parse(destination))
                    ?: return@runCatching false
                output.use { input.copyTo(it) }
                true
            }
        }.getOrDefault(false)
    }
}
