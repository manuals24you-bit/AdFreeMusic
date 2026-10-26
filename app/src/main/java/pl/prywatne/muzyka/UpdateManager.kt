package pl.prywatne.muzyka

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Aktualizacje:
 *  1. Sprawdza najnowsze wydanie NewPipeExtractor (biblioteka, która "psuje się" przy zmianach YouTube).
 *  2. Sprawdza zakładkę Releases TWOJEGO repo — GitHub Actions automatycznie buduje tam
 *     nowe APK z najnowszym extractorem.
 *  3. Pobiera APK i otwiera instalator systemowy.
 *
 * Bibliotek nie da się podmienić w działającym APK, dlatego aktualizacja = nowa wersja aplikacji.
 */
object UpdateManager {
    data class AppRelease(val tag: String, val versionCode: Int, val apkUrl: String)

    private const val EXTRACTOR_API =
        "https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest"

    private val client = OkHttpClient()

    val repoConfigured: Boolean get() = !BuildConfig.UPDATE_REPO.startsWith("TWOJ_LOGIN")

    private fun getJson(url: String): JSONObject {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub HTTP ${resp.code}")
            return JSONObject(resp.body!!.string())
        }
    }

    fun latestExtractorVersion(): String = getJson(EXTRACTOR_API).getString("tag_name")

    fun latestAppRelease(): AppRelease? {
        if (!repoConfigured) return null
        val json = getJson("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        val tag = json.getString("tag_name") // np. "v1007"
        val code = tag.filter(Char::isDigit).toIntOrNull() ?: return null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                return AppRelease(tag, code, a.getString("browser_download_url"))
            }
        }
        return null
    }

    fun download(context: Context, release: AppRelease, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "update-${release.tag}.apk")

        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Pobieranie HTTP ${resp.code}")
            val body = resp.body!!
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        return out
    }

    /** Zwraca false, jeśli najpierw trzeba zezwolić na instalację z tego źródła. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        @Suppress("DEPRECATION")
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
