package pl.prywatne.muzyka

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    var nowPlaying by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    // Aktualizacje
    var updateText by mutableStateOf<String?>(null)
    var pendingUpdate by mutableStateOf<UpdateManager.AppRelease?>(null)
        private set
    var downloadProgress by mutableStateOf<Int?>(null)
        private set

    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onMediaMetadataChanged(meta: MediaMetadata) {
            nowPlaying = meta.title?.toString()
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get().also { c ->
                c.addListener(playerListener)
                isPlaying = c.isPlaying
                nowPlaying = c.mediaMetadata.title?.toString()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            loading = true
            runCatching { withContext(Dispatchers.IO) { YouTubeRepo.search(q) } }
                .onSuccess { results = it }
                .onFailure { message = "Błąd wyszukiwania: ${it.message}" }
            loading = false
        }
    }

    /** playNow = true: zagraj od razu; false: dodaj na koniec kolejki. */
    fun play(track: Track, playNow: Boolean = true) {
        viewModelScope.launch {
            loading = true
            runCatching { withContext(Dispatchers.IO) { YouTubeRepo.resolveAudioUrl(track.url) } }
                .onSuccess { streamUrl ->
                    val item = MediaItem.Builder()
                        .setMediaId(track.url)
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.uploader)
                                .setArtworkUri(track.thumbnail?.toUri())
                                .build()
                        )
                        .build()
                    controller?.run {
                        if (playNow || mediaItemCount == 0) {
                            setMediaItem(item)
                            prepare()
                            play()
                        } else {
                            addMediaItem(item)
                            message = "Dodano do kolejki: ${track.title}"
                        }
                    }
                }
                .onFailure { message = "Nie udało się odtworzyć: ${it.message}" }
            loading = false
        }
    }

    fun togglePlay() = controller?.run { if (isPlaying) pause() else play() }
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()

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
                if (!UpdateManager.install(ctx, apk)) {
                    updateText = "Zezwól na instalację aplikacji z tego źródła i naciśnij ponownie."
                } else {
                    updateText = null
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
}
