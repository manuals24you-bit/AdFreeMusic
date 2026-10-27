package pl.prywatne.muzyka

import android.net.Uri
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
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
    val uploaderUrl: String? = null,
)

/** Wykonawca / kanał, który można zapisać w Bibliotece. */
data class ArtistRef(
    val url: String,
    val name: String,
    val avatar: String?,
)

class ChannelData(
    val artist: ArtistRef,
    val subscribers: Long,
    val tracks: List<Track>,
    val tab: ListLinkHandler?,
    val nextPage: Page?,
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

    /** Utwory: wyszukiwanie w YouTube Music (same piosenki), a w razie problemu — zwykłe. */
    fun searchSongs(query: String): List<Track> {
        val music = runCatching {
            SearchInfo.getInfo(yt, handler(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS))
                .relatedItems.filterIsInstance<StreamInfoItem>().map { it.toTrack() }
        }.getOrNull()
        return if (music.isNullOrEmpty()) searchClips(query) else music
    }

    /** Klipy: zwykłe filmy z YouTube — teledyski, lekcje, wykłady itp. */
    fun searchClips(query: String): List<Track> =
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
        return Track(
            info.name, info.url, info.uploaderName.orEmpty(), info.duration,
            info.thumbnails.best(), info.uploaderUrl
        )
    }

    /** Strona wykonawcy/kanału: dane + pierwsza strona filmów z zakładki „Filmy”. */
    fun channel(url: String): ChannelData {
        val info = ChannelInfo.getInfo(yt, url)
        val artist = ArtistRef(info.url, info.name, info.avatars.best())
        val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
            ?: info.tabs.firstOrNull { ChannelTabs.TRACKS in it.contentFilters }
            ?: info.tabs.firstOrNull()
            ?: return ChannelData(artist, info.subscriberCount, emptyList(), null, null)
        val tabInfo = ChannelTabInfo.getInfo(yt, tab)
        val tracks = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toTrack(artist) }
        return ChannelData(
            artist, info.subscriberCount, tracks, tab,
            if (tabInfo.hasNextPage()) tabInfo.nextPage else null
        )
    }

    fun channelMore(artist: ArtistRef, tab: ListLinkHandler, page: Page): Pair<List<Track>, Page?> {
        val result = ChannelTabInfo.getMoreItems(yt, tab, page)
        val tracks = result.items.filterIsInstance<StreamInfoItem>().map { it.toTrack(artist) }
        return tracks to (if (result.hasNextPage()) result.nextPage else null)
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

    /** [owner] — gdy wiemy, czyj to kanał (na stronie kanału filmy często nie mają autora). */
    private fun StreamInfoItem.toTrack(owner: ArtistRef? = null) = Track(
        title = name,
        url = url,
        uploader = uploaderName?.takeIf { it.isNotBlank() } ?: owner?.name.orEmpty(),
        durationSec = duration,
        thumbnail = thumbnails.best(),
        uploaderUrl = uploaderUrl?.takeIf { it.isNotBlank() } ?: owner?.url,
    )
}

fun List<Image>.best(): String? = maxByOrNull { it.width }?.url

fun Long.asDuration(): String =
    if (this <= 0) "" else "%d:%02d".format(this / 60, this % 60)
