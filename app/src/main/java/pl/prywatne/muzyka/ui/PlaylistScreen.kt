@file:OptIn(ExperimentalMaterial3Api::class)

package pl.prywatne.muzyka.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import pl.prywatne.muzyka.CollectionType
import pl.prywatne.muzyka.MainViewModel
import pl.prywatne.muzyka.TrackCollection

/** Strona playlisty z YouTube albo wykonawcy/kanału. */
@Composable
fun CollectionScreen(vm: MainViewModel) {
    val c = vm.currentCollection ?: return

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                }
            },
            title = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column {
                    if (c.type == CollectionType.CHANNEL) ChannelHeader(vm, c) else PlaylistHeader(c)
                    PlayButtons(vm, c)
                }
            }

            itemsIndexed(c.tracks, key = { i, t -> "$i-${t.url}" }) { i, track ->
                TrackRow(
                    track = track,
                    index = i,
                    playing = vm.now?.mediaId == track.url,
                    onClick = { vm.playCollection(i, shuffleOn = false) },
                    trailing = { TrackActions(vm, track, showArtist = c.type != CollectionType.CHANNEL) }
                )
            }

            if (c.loadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(c: TrackCollection) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Artwork(
            c.image ?: c.tracks.firstOrNull()?.thumbnail,
            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            corner = 16.dp
        )
        Spacer(Modifier.height(16.dp))
        Text(c.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val count = if (c.tracks.isNotEmpty()) "${c.tracks.size} utworów" else ""
        Text(
            listOf(c.subtitle, count).filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChannelHeader(vm: MainViewModel, c: TrackCollection) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(
                model = c.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            c.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (c.subtitle.isNotEmpty()) {
            Text(
                c.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        val artist = c.artist
        if (artist != null) {
            val saved = vm.isArtistSaved(artist.url)
            if (saved) {
                FilledTonalButton(onClick = { vm.toggleArtist(artist) }) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Zapisany")
                }
            } else {
                OutlinedButton(onClick = { vm.toggleArtist(artist) }) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Zapisz wykonawcę")
                }
            }
        }
    }
}

@Composable
private fun PlayButtons(vm: MainViewModel, c: TrackCollection) {
    Row(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { vm.playCollection(0, shuffleOn = false) },
            enabled = c.tracks.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Odtwórz")
        }
        FilledTonalButton(
            onClick = { vm.playCollection(0, shuffleOn = true) },
            enabled = c.tracks.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Shuffle, null)
            Spacer(Modifier.width(8.dp))
            Text("Losowo")
        }
    }
}
