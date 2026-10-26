@file:OptIn(ExperimentalMaterial3Api::class)

package pl.prywatne.muzyka.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.prywatne.muzyka.MainViewModel
import pl.prywatne.muzyka.SearchKind

@Composable
fun SearchScreen(vm: MainViewModel) {
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Muzyka", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { vm.checkUpdates() }) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Sprawdź aktualizacje")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        TextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Szukaj lub wklej link z YouTube") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.query = "" }) { Icon(Icons.Default.Clear, "Wyczyść") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.search()
            })
        )

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vm.kind == SearchKind.SONGS,
                onClick = { vm.changeKind(SearchKind.SONGS) },
                label = { Text("Utwory") },
                leadingIcon = { Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = vm.kind == SearchKind.CLIPS,
                onClick = { vm.changeKind(SearchKind.CLIPS) },
                label = { Text("Klipy") },
                leadingIcon = { Icon(Icons.Default.SmartDisplay, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = vm.kind == SearchKind.PLAYLISTS,
                onClick = { vm.changeKind(SearchKind.PLAYLISTS) },
                label = { Text("Playlisty") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.size(18.dp)) }
            )
        }

        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        val empty = when (vm.kind) {
            SearchKind.SONGS -> vm.songs.isEmpty()
            SearchKind.CLIPS -> vm.clips.isEmpty()
            SearchKind.PLAYLISTS -> vm.playlists.isEmpty()
        }
        if (empty && !vm.loading) {
            EmptyHint()
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (vm.kind) {
                SearchKind.SONGS -> itemsIndexed(vm.songs, key = { i, t -> "$i-${t.url}" }) { i, track ->
                    TrackRow(
                        track = track,
                        playing = vm.now?.mediaId == track.url,
                        onClick = { vm.playFromResults(i) },
                        trailing = {
                            IconButton(onClick = { vm.enqueue(track) }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Dodaj do kolejki")
                            }
                        }
                    )
                }
                SearchKind.CLIPS -> itemsIndexed(vm.clips, key = { i, t -> "$i-${t.url}" }) { i, clip ->
                    ClipCard(
                        clip = clip,
                        playing = vm.now?.mediaId == clip.url,
                        onClick = { vm.playClip(i) },
                        onQueue = { vm.enqueue(clip) }
                    )
                }
                SearchKind.PLAYLISTS -> itemsIndexed(vm.playlists, key = { i, p -> "$i-${p.url}" }) { _, playlist ->
                    PlaylistRow(playlist) { vm.showPlaylist(playlist) }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.LibraryMusic, null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Wpisz nazwę utworu albo wklej link do filmu lub playlisty z YouTube.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
