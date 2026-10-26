package pl.prywatne.muzyka

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Tryb audio: jeden strumień dźwięku.
 * Tryb wideo: osobny strumień obrazu (do 720p) + dźwięku, łączone w locie.
 */
@OptIn(UnstableApi::class)
class YtMediaSourceFactory(context: Context) : MediaSource.Factory {

    private val dataSourceFactory: DataSource.Factory = ResolvingDataSource.Factory(
        ChunkedDataSource.Factory(
            DefaultHttpDataSource.Factory()
                .setUserAgent(DownloaderImpl.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
        ),
        StreamResolver.resolver
    )

    private val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)
    private val fallback = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        progressive.setDrmSessionManagerProvider(drmSessionManagerProvider)
        fallback.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        progressive.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        fallback.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = fallback.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        if (uri?.scheme != Yt.SCHEME) return fallback.createMediaSource(mediaItem)

        val videoUrl = uri.getQueryParameter("u") ?: mediaItem.mediaId
        // Pierwsze źródło niesie oryginalny MediaItem (tytuł, okładka) — to on trafia do powiadomienia.
        val audio = progressive.createMediaSource(
            mediaItem.buildUpon().setUri(Yt.uriFor(videoUrl, "a")).build()
        )
        if (!PlayerMode.video) return audio

        val video = progressive.createMediaSource(
            mediaItem.buildUpon().setUri(Yt.uriFor(videoUrl, "v")).build()
        )
        return MergingMediaSource(true, true, audio, video)
    }
}
