package pl.prywatne.muzyka

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import kotlin.random.Random

enum class SearchKind { SONGS, CLIPS, PLAYLISTS }

enum class MainTab { SEARCH, LIBRARY }

enum class CollectionType { PLAYLIST, CHANNEL }

/** Otwarta playlista albo strona wykonawcy — lista utworów do odtworzenia. */
data class TrackCollection(
    val key: String,
    val type: CollectionType,
    val title: String,
    val subtitle: String,
    val image: String?,
    val artist: ArtistRef?,      // tylko dla kanału (do przycisku „Zapisz”)
    val tracks: List<Track>,
    val loadingMore: Boolean,
)

data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val art: Uri?,
    val uploaderUrl: String?,
    val durationSec: Long,
)

data class QueueEntry(val title: String, val artist: String, val art: Uri?)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- Wyszukiwanie ----------
    var query by mutableStateOf("")
    var kind by mutableStateOf(SearchKind.SONGS)
        private set
    var songs by mutableStateOf<List<Track>>(emptyList())
        private set
    var clips by mutableStateOf<List<Track>>(emptyList())
        private set
    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    private var songsQuery = ""
    private var clipsQuery = ""
    private var playlistsQuery = ""

    // ---------- Nawigacja ----------
    var tab by mutableStateOf(MainTab.SEARCH)
        private set
    /** Stos otwartych stron (playlisty / wykonawcy); ostatnia jest widoczna. */
    val pages = mutableStateListOf<String>()
    private val collections = mutableStateMapOf<String, TrackCollection>()
    val currentCollection: TrackCollection?
        get() = pages.lastOrNull()?.let { collections[it] }
    private var activePlaylistKey: String? = null

    // ---------- Biblioteka (tylko na telefonie, wspólna dla telefonu i auta) ----------
    val favorites: List<Track> get() = LibraryStore.favorites
    val favoriteUrls: Set<String> get() = LibraryStore.favoriteUrls
    val artists: List<ArtistRef> get() = LibraryStore.artists

    // ---------- Odtwarzacz ----------
    var controller by mutableStateOf<MediaController?>(null)
        private set
    var now by mutableStateOf<NowPlaying?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queue by mutableStateOf<List<QueueEntry>>(emptyList())
        private set
    var currentIndex by mutableStateOf(-1)
        private set
    val videoMode: Boolean get() = PlayerMode.video
    var playerOpen by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var speed by mutableStateOf(1f)
        private set

    // ---------- Aktualizacje ----------
    var updateText by mutableStateOf<String?>(null)
    var pendingUpdate by mutableStateOf<UpdateManager.AppRelease?>(null)
        private set
    var downloadProgress by mutableStateOf<Int?>(null)
        private set

    private val controllerFuture: ListenableFuture<MediaController>

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(player, events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onPlayerError(error: PlaybackException) {
            message = "Problem z odtwarzaniem: ${error.message}"
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            val c = controllerFuture.get()
            c.addListener(playerListener)
            controller = c
            refresh(c, rebuildQueue = true)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun refresh(p: Player, rebuildQueue: Boolean) {
        now = p.currentMediaItem?.let {
            val extras = it.mediaMetadata.extras
            NowPlaying(
                mediaId = it.mediaId,
                title = it.mediaMetadata.title?.toString().orEmpty(),
                artist = it.mediaMetadata.artist?.toString().orEmpty(),
                art = it.mediaMetadata.artworkUri,
                uploaderUrl = extras?.getString(EXTRA_UPLOADER_URL),
                durationSec = extras?.getLong(EXTRA_DURATION) ?: 0L,
            )
        }
        isPlaying = p.isPlaying
        isBuffering = p.playbackState == Player.STATE_BUFFERING
        durationMs = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        shuffle = p.shuffleModeEnabled
        repeatMode = p.repeatMode
        currentIndex = p.currentMediaItemIndex
        speed = p.playbackParameters.speed
        if (rebuildQueue) {
            queue = (0 until p.mediaItemCount).map { i ->
                val m = p.getMediaItemAt(i).mediaMetadata
                QueueEntry(m.title?.toString().orEmpty(), m.artist?.toString().orEmpty(), m.artworkUri)
            }
        }
    }

    // ---------- Wyszukiwanie ----------

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return

        when (val link = YouTubeRepo.parseLink(q)) {
            is LinkTarget.Playlist -> {
                showPlaylist(PlaylistRef("Playlista", link.url, "", 0, null))
                return
            }
            is LinkTarget.Video -> {
                playLink(link.url)
                return
            }
            null -> Unit
        }

        val searchKind = kind
        viewModelScope.launch {
            loading = true
            try {
                when (searchKind) {
                    SearchKind.SONGS -> {
                        songs = withContext(Dispatchers.IO) { YouTubeRepo.searchSongs(q) }
                        songsQuery = q
                    }
                    SearchKind.CLIPS -> {
                        clips = withContext(Dispatchers.IO) { YouTubeRepo.searchClips(q) }
                        clipsQuery = q
                    }
                    SearchKind.PLAYLISTS -> {
                        playlists = withContext(Dispatchers.IO) { YouTubeRepo.searchPlaylists(q) }
                        playlistsQuery = q
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Błąd wyszukiwania: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun changeKind(newKind: SearchKind) {
        kind = newKind
        val q = query.trim()
        val last = when (newKind) {
            SearchKind.SONGS -> songsQuery
            SearchKind.CLIPS -> clipsQuery
            SearchKind.PLAYLISTS -> playlistsQuery
        }
        if (q.isNotEmpty() && q != last) search()
    }

    private fun playLink(url: String) {
        viewModelScope.launch {
            loading = true
            try {
                val track = withContext(Dispatchers.IO) { YouTubeRepo.video(url) }
                playTracks(listOf(track), 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Nie udało się otworzyć linku: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // ---------- Odtwarzanie ----------

    /** start < 0 przy shuffle = losowy pierwszy utwór. */
    private fun playTracks(tracks: List<Track>, start: Int, shuffleOn: Boolean = false, sourceKey: String? = null) {
        val c = controller
        if (c == null) {
            message = "Odtwarzacz jeszcze się uruchamia, spróbuj za chwilę"
            return
        }
        if (tracks.isEmpty()) return
        activePlaylistKey = sourceKey
        val first = if (start < 0) Random.nextInt(tracks.size) else start.coerceIn(0, tracks.size - 1)
        c.shuffleModeEnabled = shuffleOn
        c.setMediaItems(tracks.map { it.toMediaItem() }, first, 0L)
        c.prepare()
        c.play()
    }

    /** Kliknięcie w wynik wyszukiwania: gra ten utwór, a potem kolejne wyniki. */
    fun playFromResults(index: Int) {
        playTracks(songs, index)
    }

    /** Kliknięcie klipu: włącza obraz, gra klip i kolejne wyniki, otwiera odtwarzacz. */
    fun playClip(index: Int) {
        if (controller == null) {
            message = "Odtwarzacz jeszcze się uruchamia, spróbuj za chwilę"
            return
        }
        setVideoMode(true, reloadQueue = false) // kolejka i tak zaraz zostanie zastąpiona
        playTracks(clips, index)
        playerOpen = true
    }

    fun enqueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
        } else {
            c.addMediaItem(track.toMediaItem())
            message = "Dodano do kolejki: ${track.title}"
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun changeVideoMode(on: Boolean) = setVideoMode(on, reloadQueue = true)

    private val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

    /** Prędkość odtwarzania: 0,75× → 1× → 1,25× → 1,5× → 2× → 0,75× … */
    fun cycleSpeed() {
        val c = controller ?: return
        val next = speeds.firstOrNull { it > c.playbackParameters.speed + 0.01f } ?: speeds.first()
        c.setPlaybackSpeed(next)
    }

    /** Cofnij / przewiń o podaną liczbę milisekund (np. -10 000). */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = c.currentPosition + deltaMs
        val max = if (c.duration > 0) c.duration else Long.MAX_VALUE
        c.seekTo(target.coerceIn(0L, max))
    }

    private fun setVideoMode(on: Boolean, reloadQueue: Boolean) {
        if (on == videoMode) return
        PlayerMode.video = on
        if (!on) fullscreen = false
        getApplication<Application>()
            .getSharedPreferences(App.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(App.KEY_VIDEO, on).apply()

        if (!reloadQueue) return
        // Przeładuj kolejkę w nowym trybie, zachowując utwór i pozycję.
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        val wasPlaying = c.playWhenReady
        c.setMediaItems(items, index, position)
        c.prepare()
        c.playWhenReady = wasPlaying
    }

    // ---------- Nawigacja ----------

    fun selectTab(newTab: MainTab) {
        pages.clear()
        tab = newTab
    }

    fun back() {
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }

    private fun push(key: String) {
        if (pages.lastOrNull() != key) pages.add(key)
    }

    private fun update(key: String, change: (TrackCollection) -> TrackCollection) {
        collections[key]?.let { collections[key] = change(it) }
    }

    /** Dociąga kolejne strony utworów (do [max]) i — jeśli ta lista właśnie gra — dopisuje je do kolejki. */
    private suspend fun loadRest(
        key: String,
        first: List<Track>,
        firstNext: Page?,
        max: Int,
        fetch: (Page) -> Pair<List<Track>, Page?>,
    ) {
        var tracks = first
        var page = firstNext
        while (true) {
            val current = page ?: break
            if (tracks.size >= max) break
            val (more, next) = withContext(Dispatchers.IO) { fetch(current) }
            tracks = tracks + more
            if (activePlaylistKey == key) controller?.addMediaItems(more.map { it.toMediaItem() })
            page = if (more.isEmpty()) null else next
            val stillLoading = page != null && tracks.size < max
            val snapshot = tracks
            update(key) { it.copy(tracks = snapshot, loadingMore = stillLoading) }
        }
        update(key) { it.copy(loadingMore = false) }
    }

    private fun loadCollection(key: String, what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Nie udało się wczytać $what: ${e.message}"
                collections.remove(key)
                if (pages.lastOrNull() == key) back()
            }
        }
    }

    // ---------- Playlisty z YouTube ----------

    fun showPlaylist(ref: PlaylistRef) {
        val key = "pl:" + ref.url
        push(key)
        if (key in collections) return
        collections[key] = TrackCollection(
            key, CollectionType.PLAYLIST, ref.title, ref.uploader, ref.thumbnail,
            artist = null, tracks = emptyList(), loadingMore = true
        )
        loadCollection(key, "playlisty") {
            val data = withContext(Dispatchers.IO) { YouTubeRepo.playlist(ref.url) }
            update(key) {
                it.copy(
                    title = data.ref.title,
                    subtitle = data.ref.uploader,
                    image = data.ref.thumbnail ?: ref.thumbnail ?: data.tracks.firstOrNull()?.thumbnail,
                    tracks = data.tracks,
                    loadingMore = data.nextPage != null,
                )
            }
            loadRest(key, data.tracks, data.nextPage, MAX_PLAYLIST) { page ->
                YouTubeRepo.playlistMore(ref.url, page)
            }
        }
    }

    // ---------- Wykonawcy / kanały ----------

    fun showChannel(channelUrl: String, name: String) {
        // Linki z YouTube Music prowadzą do tego samego kanału na zwykłym YouTube.
        val url = channelUrl.replace("music.youtube.com", "www.youtube.com")
        playerOpen = false
        fullscreen = false
        val key = "ch:$url"
        push(key)
        if (key in collections) return
        collections[key] = TrackCollection(
            key, CollectionType.CHANNEL, name, "", null,
            artist = ArtistRef(url, name, null), tracks = emptyList(), loadingMore = true
        )
        loadCollection(key, "wykonawcy") {
            val data = withContext(Dispatchers.IO) { YouTubeRepo.channel(url) }
            val subs = if (data.subscribers > 0) "${formatCount(data.subscribers)} subskrybentów" else ""
            update(key) {
                it.copy(
                    title = data.artist.name,
                    subtitle = subs,
                    image = data.artist.avatar,
                    artist = data.artist.copy(url = url),
                    tracks = data.tracks,
                    loadingMore = data.nextPage != null,
                )
            }
            // Zapisany wykonawca dostaje aktualny awatar i nazwę.
            if (isArtistSaved(url)) {
                LibraryStore.updateArtists(artists.map { a -> if (a.url == url) data.artist.copy(url = url) else a })
            }
            val channelTab = data.tab
            if (channelTab != null) {
                loadRest(key, data.tracks, data.nextPage, MAX_CHANNEL) { page ->
                    YouTubeRepo.channelMore(data.artist, channelTab, page)
                }
            } else {
                update(key) { it.copy(loadingMore = false) }
            }
        }
    }

    fun openArtistOf(track: Track) {
        val url = track.uploaderUrl
        if (url.isNullOrBlank()) message = "Brak informacji o wykonawcy" else showChannel(url, track.uploader)
    }

    fun openArtistNow() {
        val n = now ?: return
        val url = n.uploaderUrl
        if (url.isNullOrBlank()) message = "Brak informacji o wykonawcy" else showChannel(url, n.artist)
    }

    fun playCollection(start: Int, shuffleOn: Boolean) {
        val c = currentCollection ?: return
        playTracks(c.tracks, if (shuffleOn) -1 else start, shuffleOn, c.key)
    }

    // ---------- Ulubione i zapisani wykonawcy ----------

    fun isFavorite(url: String) = url in favoriteUrls

    fun toggleFavorite(track: Track) {
        LibraryStore.updateFavorites(
            if (track.url in favoriteUrls) {
                favorites.filterNot { it.url == track.url }
            } else {
                message = "Dodano do ulubionych"
                listOf(track) + favorites
            }
        )
    }

    fun toggleFavoriteNow() {
        val n = now ?: return
        val track = favorites.firstOrNull { it.url == n.mediaId } ?: Track(
            title = n.title,
            url = n.mediaId,
            uploader = n.artist,
            durationSec = if (n.durationSec > 0) n.durationSec else durationMs / 1000,
            thumbnail = n.art?.toString(),
            uploaderUrl = n.uploaderUrl,
        )
        toggleFavorite(track)
    }

    fun playFavorites(start: Int, shuffleOn: Boolean) {
        playTracks(favorites, if (shuffleOn) -1 else start, shuffleOn)
    }

    fun isArtistSaved(url: String) = artists.any { it.url == url }

    fun toggleArtist(artist: ArtistRef) {
        LibraryStore.updateArtists(
            if (isArtistSaved(artist.url)) {
                artists.filterNot { it.url == artist.url }
            } else {
                message = "Zapisano: ${artist.name}"
                artists + artist
            }
        )
    }

    // ---------- Aktualizacje ----------

    fun checkUpdates() {
        viewModelScope.launch {
            pendingUpdate = null
            updateText = "Sprawdzam aktualizacje…"
            runCatching {
                withContext(Dispatchers.IO) {
                    UpdateManager.latestExtractorVersion() to UpdateManager.latestAppRelease()
                }
            }.onSuccess { (latestExtractor, release) ->
                val mine = BuildConfig.EXTRACTOR_VERSION
                val extractorLine = if (latestExtractor == mine) {
                    "Silnik YouTube (NewPipeExtractor): aktualny $mine"
                } else {
                    "Nowy silnik YouTube: $latestExtractor (masz $mine)"
                }
                val appLine = when {
                    !UpdateManager.repoConfigured ->
                        "Ustaw updateRepo w gradle.properties, żeby pobierać nowe APK."
                    release != null && release.versionCode > BuildConfig.VERSION_CODE -> {
                        pendingUpdate = release
                        "Dostępna nowa wersja aplikacji: ${release.tag}"
                    }
                    latestExtractor != mine ->
                        "Nowe APK jeszcze się buduje na GitHubie — sprawdź za kilkanaście minut."
                    else -> "Aplikacja jest aktualna (v${BuildConfig.VERSION_NAME})."
                }
                updateText = "$extractorLine\n\n$appLine"
            }.onFailure {
                updateText = "Nie udało się sprawdzić: ${it.message}"
            }
        }
    }

    fun installUpdate() {
        val release = pendingUpdate ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            downloadProgress = 0
            runCatching {
                withContext(Dispatchers.IO) {
                    UpdateManager.download(ctx, release) { p ->
                        viewModelScope.launch { downloadProgress = p }
                    }
                }
            }.onSuccess { apk ->
                downloadProgress = null
                updateText = if (!UpdateManager.install(ctx, apk)) {
                    "Zezwól na instalację aplikacji z tego źródła i naciśnij ponownie."
                } else {
                    null
                }
            }.onFailure {
                downloadProgress = null
                updateText = "Błąd pobierania: ${it.message}"
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
    }

    private companion object {
        const val MAX_PLAYLIST = 500
        const val MAX_CHANNEL = 200
    }
}

private const val EXTRA_UPLOADER_URL = "uploaderUrl"
private const val EXTRA_DURATION = "durationSec"

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(url)
    .setUri(Yt.uriFor(url))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(uploader)
            .setArtworkUri(thumbnail?.toUri())
            .setExtras(Bundle().apply {
                putString(EXTRA_UPLOADER_URL, uploaderUrl)
                putLong(EXTRA_DURATION, durationSec)
            })
            .build()
    )
    .build()

/** 1 234 567 → „1,2 mln”, 45 600 → „45,6 tys.” */
fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1f mln".format(n / 1_000_000.0).replace('.', ',')
    n >= 1_000 -> "%.1f tys.".format(n / 1_000.0).replace('.', ',')
    else -> n.toString()
}
