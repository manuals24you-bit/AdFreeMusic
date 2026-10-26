package pl.prywatne.muzyka

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Tryb odtwarzania: false = tylko dźwięk, true = obraz + dźwięk. */
object PlayerMode {
    @Volatile
    var video: Boolean = false
}

/**
 * Elementy kolejki mają adresy "yt://s?u=<link do filmu>&k=a|v".
 * Prawdziwy adres strumienia jest ustalany dopiero w chwili odtwarzania, więc:
 *  - playlista z 500 utworami trafia do kolejki od razu (bez 500 zapytań),
 *  - adresy nie wygasają, zanim dojdzie do danego utworu.
 */
object Yt {
    const val SCHEME = "yt"

    fun uriFor(videoUrl: String, kind: String = "a"): Uri =
        Uri.Builder().scheme(SCHEME).authority("s")
            .appendQueryParameter("u", videoUrl)
            .appendQueryParameter("k", kind)
            .build()
}

@OptIn(UnstableApi::class)
object StreamResolver {
    private const val TTL_MS = 4 * 60 * 60 * 1000L

    private class Entry(val audio: String, val video: String?, val time: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Any>()

    private fun fresh(e: Entry?) = e != null && System.currentTimeMillis() - e.time < TTL_MS

    private fun entry(videoUrl: String): Entry {
        cache[videoUrl]?.let { if (fresh(it)) return it }
        val lock = locks.getOrPut(videoUrl) { Any() }
        synchronized(lock) {
            cache[videoUrl]?.let { if (fresh(it)) return it }
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val e = Entry(pickAudio(info), pickVideo(info), System.currentTimeMillis())
            cache[videoUrl] = e
            return e
        }
    }

    fun invalidate(videoUrl: String) {
        cache.remove(videoUrl)
    }

    private fun pickAudio(info: StreamInfo): String {
        val all = info.audioStreams.filter { it.isUrl }
        // Pomijamy dubbingowane ścieżki, jeśli jest oryginalna.
        val original = all.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { all }
        val best = original
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
            ?: original.firstOrNull()
            ?: throw IOException("Brak strumienia audio")
        return best.content
    }

    /** Do 720p w H.264 — działa sprzętowo na praktycznie każdym telefonie. */
    private fun pickVideo(info: StreamInfo): String? {
        val videoOnly = info.videoOnlyStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val best = videoOnly
            .filter { it.codec?.startsWith("avc") == true && it.height in 1..720 }
            .maxByOrNull { it.height }
            ?: videoOnly.filter { it.format == MediaFormat.MPEG_4 && it.height in 1..720 }
                .maxByOrNull { it.height }
            ?: info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .maxByOrNull { it.height }
        return best?.content
    }

    val resolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            if (uri.scheme != Yt.SCHEME) return dataSpec
            val videoUrl = uri.getQueryParameter("u") ?: throw IOException("Brak adresu filmu")
            val real = try {
                val e = entry(videoUrl)
                if (uri.getQueryParameter("k") == "v") (e.video ?: e.audio) else e.audio
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException(e.message ?: "Nie udało się pobrać strumienia", e)
            }
            return dataSpec.withUri(Uri.parse(real))
        }
    }
}
