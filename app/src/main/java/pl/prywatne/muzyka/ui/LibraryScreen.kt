@file:OptIn(ExperimentalMaterial3Api::class)

package pl.prywatne.muzyka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.prywatne.muzyka.MainViewModel

/** Biblioteka: zapisani wykonawcy i ulubione utwory (przechowywane tylko na telefonie). */
@Composable
fun LibraryScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Biblioteka", fontWeight = FontWeight.Bold) },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { SectionTitle("Zapisani wykonawcy") }
            if (vm.artists.isEmpty()) {
                item {
                    Hint("Otwórz stronę wykonawcy (menu ⋮ przy utworze albo nazwa wykonawcy w odtwarzaczu) i stuknij „Zapisz wykonawcę”.")
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(vm.artists, key = { it.url }) { artist ->
                            ArtistBubble(artist) { vm.showChannel(artist.url, artist.name) }
                        }
                    }
                }
            }

            item { SectionTitle("Ulubione · ${vm.favorites.size}") }
            if (vm.favorites.isEmpty()) {
                item { Hint("Stuknij ♡ przy utworze lub w odtwarzaczu, żeby dodać go do ulubionych.") }
            } else {
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = { vm.playFavorites(0, shuffleOn = false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Odtwórz")
                        }
                        FilledTonalButton(onClick = { vm.playFavorites(0, shuffleOn = true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Shuffle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Losowo")
                        }
                    }
                }
                itemsIndexed(vm.favorites, key = { _, t -> t.url }) { i, track ->
                    TrackRow(
                        track = track,
                        playing = vm.now?.mediaId == track.url,
                        onClick = { vm.playFavorites(i, shuffleOn = false) },
                        trailing = { TrackActions(vm, track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
