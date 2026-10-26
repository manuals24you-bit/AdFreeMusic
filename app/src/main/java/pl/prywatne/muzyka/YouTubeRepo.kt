package pl.prywatne.muzyka

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class Track(
    val title: String,
    val url: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnail: String?,
)

/** Wszystkie wywołania są blokujące — uruchamiaj je na Dispatchers.IO. */
object YouTubeRepo {
    private val yt get() = ServiceList.YouTube

    fun search(query: String): List<Track> {
        val handler = yt.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            ""
        )
        return SearchInfo.getInfo(yt, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map {
                Track(
                    title = it.name,
                    url = it.url,
                    uploader = it.uploaderName.orEmpty(),
                    durationSec = it.duration,
                    thumbnail = it.thumbnails.maxByOrNull { img -> img.width }?.url,
                )
            }
    }

    /** Zwraca bezpośredni adres strumienia audio — bez odtwarzacza YouTube, więc bez reklam. */
    fun resolveAudioUrl(videoUrl: String): String {
        val streams = StreamInfo.getInfo(yt, videoUrl).audioStreams
        val best = streams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .maxByOrNull { it.averageBitrate }
            ?: streams.firstOrNull { it.isUrl }
            ?: error("Nie znaleziono strumienia audio")
        return best.content
    }
}

fun Long.asDuration(): String =
    if (this <= 0) "" else "%d:%02d".format(this / 60, this % 60)
