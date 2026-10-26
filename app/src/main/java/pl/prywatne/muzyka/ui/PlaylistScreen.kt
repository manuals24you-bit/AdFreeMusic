@file:OptIn(ExperimentalMaterial3Api::class)

package pl.prywatne.muzyka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.prywatne.muzyka.MainViewModel

@Composable
fun PlaylistScreen(vm: MainViewModel) {
    val p = vm.openPlaylist ?: return

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { vm.closePlaylist() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                }
            },
            title = { Text(p.ref.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Artwork(
                        p.ref.thumbnail ?: p.tracks.firstOrNull()?.thumbnail,
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        corner = 16.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        p.ref.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val count = if (p.ref.count > 0) p.ref.count else p.tracks.size.toLong()
                    Text(
                        listOf(p.ref.uploader, if (count > 0) "$count utworów" else "")
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { vm.playPlaylist(0, shuffleOn = false) },
                            enabled = p.tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Odtwórz")
                        }
                        FilledTonalButton(
                            onClick = { vm.playPlaylist(0, shuffleOn = true) },
                            enabled = p.tracks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Losowo")
                        }
                    }
                }
            }

            itemsIndexed(p.tracks, key = { i, t -> "$i-${t.url}" }) { i, track ->
                TrackRow(
                    track = track,
                    index = i,
                    playing = vm.now?.mediaId == track.url,
                    onClick = { vm.playPlaylist(i, shuffleOn = false) },
                    trailing = {
                        IconButton(onClick = { vm.enqueue(track) }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Dodaj do kolejki")
                        }
                    }
                )
            }

            if (p.loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}
