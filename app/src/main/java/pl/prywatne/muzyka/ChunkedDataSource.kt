package pl.prywatne.muzyka

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * YouTube spowalnia lub zrywa długie, ciągłe pobieranie strumienia.
 * Ten DataSource pobiera plik kawałkami po 10 MB (parametr "range" w adresie),
 * tak jak robi to NewPipe. Dzięki temu wideo i długie utwory nie zacinają się.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(private val upstream: DataSource) : DataSource {

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstreamFactory.createDataSource())
    }

    private var base: DataSpec? = null
    private var chunked = false
    private var opened = false
    private var position = 0L
    private var end = 0L
    private var chunkLeft = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val clen = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        val host = dataSpec.uri.host.orEmpty()
        if (clen == null || !host.endsWith("googlevideo.com")) {
            chunked = false
            opened = true
            return upstream.open(dataSpec)
        }
        chunked = true
        base = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            minOf(clen, position + dataSpec.length)
        } else {
            clen
        }
        if (position >= end) {
            chunkLeft = 0
            return 0
        }
        openChunk()
        return end - position
    }

    private fun openChunk() {
        val spec = base ?: return
        val chunkEnd = minOf(position + CHUNK, end)
        val uri = spec.uri.buildUpon()
            .appendQueryParameter("range", "$position-${chunkEnd - 1}")
            .build()
        upstream.open(
            spec.buildUpon()
                .setUri(uri)
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()
        )
        opened = true
        chunkLeft = chunkEnd - position
    }

    private fun reopen() {
        if (opened) upstream.close()
        opened = false
        openChunk()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!chunked) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (position >= end) return C.RESULT_END_OF_INPUT
        if (chunkLeft <= 0) reopen()

        var n = upstream.read(buffer, offset, minOf(length.toLong(), chunkLeft).toInt())
        if (n == C.RESULT_END_OF_INPUT) {
            // Serwer zakończył kawałek wcześniej — dociągamy resztę od bieżącej pozycji.
            reopen()
            n = upstream.read(buffer, offset, minOf(length.toLong(), chunkLeft).toInt())
            if (n == C.RESULT_END_OF_INPUT) return n
        }
        position += n
        chunkLeft -= n
        return n
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        base = null
        if (opened) upstream.close()
        opened = false
    }

    private companion object {
        const val CHUNK = 10L * 1024 * 1024
    }
}
