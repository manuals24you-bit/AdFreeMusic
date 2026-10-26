package pl.prywatne.muzyka

import android.net.Uri
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class Track(
    val title: String,
    val url: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnail: String?,
)

data class PlaylistRef(
    val title: String,
    val url: String,
    val uploader: String,
    val count: Long,
    val thumbnail: String?,
)

class PlaylistData(val ref: PlaylistRef, val tracks: List<Track>, val nextPage: Page?)

sealed interface LinkTarget {
    data class Video(val url: String) : LinkTarget
    data class Playlist(val url: String) : LinkTarget
}

/** Wszystkie wywołania są blokujące — uruchamiaj je na Dispatchers.IO. */
object YouTubeRepo {
    private val yt: StreamingService get() = ServiceList.YouTube

    private fun handler(query: String, filter: String) =
        yt.searchQHFactory.fromQuery(query, listOf(filter), "")

    fun searchSongs(query: String): List<Track> =
        SearchInfo.getInfo(yt, handler(query, YoutubeSearchQueryHandlerFactory.VIDEOS))
            .relatedItems.filterIsInstance<StreamInfoItem>().map { it.toTrack() }

    fun searchPlaylists(query: String): List<PlaylistRef> =
        SearchInfo.getInfo(yt, handler(query, YoutubeSearchQueryHandlerFactory.PLAYLISTS))
            .relatedItems.filterIsInstance<PlaylistInfoItem>().map {
                PlaylistRef(
                    title = it.name,
                    url = it.url,
                    uploader = it.uploaderName.orEmpty(),
                    count = it.streamCount,
                    thumbnail = it.thumbnails.best(),
                )
            }

    fun playlist(url: String): PlaylistData {
        val info = PlaylistInfo.getInfo(yt, url)
        val ref = PlaylistRef(
            title = info.name,
            url = info.url,
            uploader = info.uploaderName.orEmpty(),
            count = info.streamCount,
            thumbnail = info.thumbnails.best(),
        )
        val tracks = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toTrack() }
        return PlaylistData(ref, tracks, if (info.hasNextPage()) info.nextPage else null)
    }

    fun playlistMore(url: String, page: Page): Pair<List<Track>, Page?> {
        val result = PlaylistInfo.getMoreItems(yt, url, page)
        val tracks = result.items.filterIsInstance<StreamInfoItem>().map { it.toTrack() }
        return tracks to (if (result.hasNextPage()) result.nextPage else null)
    }

    fun video(url: String): Track {
        val info = StreamInfo.getInfo(yt, url)
        return Track(info.name, info.url, info.uploaderName.orEmpty(), info.duration, info.thumbnails.best())
    }

    /** Rozpoznaje wklejony link do filmu lub playlisty z YouTube. */
    fun parseLink(text: String): LinkTarget? {
        var t = text.trim()
        if (!t.contains("youtu")) return null
        if (!t.startsWith("http")) t = "https://$t"
        val uri = Uri.parse(t)
        if (!uri.host.orEmpty().contains("youtu")) return null
        if (!uri.getQueryParameter("list").isNullOrBlank()) return LinkTarget.Playlist(t)
        val type = runCatching { yt.getLinkTypeByUrl(t) }.getOrNull()
        return if (type == StreamingService.LinkType.STREAM) LinkTarget.Video(t) else null
    }

    private fun StreamInfoItem.toTrack() = Track(
        title = name,
        url = url,
        uploader = uploaderName.orEmpty(),
        durationSec = duration,
        thumbnail = thumbnails.best(),
    )
}

fun List<Image>.best(): String? = maxByOrNull { it.width }?.url

fun Long.asDuration(): String =
    if (this <= 0) "" else "%d:%02d".format(this / 60, this % 60)
