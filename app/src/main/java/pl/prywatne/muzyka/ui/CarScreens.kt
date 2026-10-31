@file:OptIn(ExperimentalMaterial3Api::class)

package pl.prywatne.muzyka.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.prywatne.muzyka.MainTab
import pl.prywatne.muzyka.MainViewModel
import pl.prywatne.muzyka.SearchKind

/** Klawiatura na ekranie auta (dostarcza ją HoodifyCarActivity). */
interface CarInput {
    fun start(initial: String, onChange: (String) -> Unit, onSubmit: () -> Unit)
    fun stop()
}

private enum class CarTab { SEARCH, LIBRARY, PLAYER, QUEUE }

private val CarBackground = Color(0xFF0E0E11)

/** Cały interfejs Hoodify na ekranie samochodu (poziomy układ, duże przyciski, bez okienek). */
@Composable
fun CarApp(vm: MainViewModel, input: CarInput) {
    val scheme = darkColorScheme(
        primary = Accent,
        secondary = Accent,
        background = CarBackground,
        surface = Color(0xFF16161B),
        surfaceVariant = Color(0xFF24242C),
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalCarMode provides true) {
            CarRoot(vm, input)
        }
    }
}

@Composable
private fun CarRoot(vm: MainViewModel, input: CarInput) {
    var tab by remember { mutableStateOf(CarTab.SEARCH) }
    var browseTab by remember { mutableStateOf(CarTab.SEARCH) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    // Kliknięcie klipu w telefonie-owym kodzie otwiera odtwarzacz — w aucie przełączamy zakładkę.
    LaunchedEffect(vm.playerOpen) {
        if (vm.playerOpen) {
            tab = CarTab.PLAYER
            vm.playerOpen = false
        }
    }
    // Otwarcie wykonawcy/playlisty z odtwarzacza lub kolejki — wracamy do przeglądania.
    LaunchedEffect(vm.pages.size) {
        if (vm.pages.isNotEmpty() && (tab == CarTab.PLAYER || tab == CarTab.QUEUE)) tab = browseTab
    }

    BackHandler(enabled = tab == CarTab.PLAYER || tab == CarTab.QUEUE) { tab = browseTab }
    BackHandler(enabled = (tab == CarTab.SEARCH || tab == CarTab.LIBRARY) && vm.pages.isNotEmpty()) { vm.back() }

    fun go(t: CarTab) {
        if (t == CarTab.SEARCH || t == CarTab.LIBRARY) {
            val target = if (t == CarTab.SEARCH) MainTab.SEARCH else MainTab.LIBRARY
            when {
                tab == t && vm.pages.isNotEmpty() -> vm.selectTab(target) // drugi klik = powrót na start
                t != browseTab -> vm.selectTab(target)
            }
            browseTab = t
        }
        tab = t
    }

    Row(Modifier.fillMaxSize().background(CarBackground)) {
        NavigationRail(containerColor = Color.Black) {
            Spacer(Modifier.height(8.dp))
            RailItem(Icons.Default.Search, "Szukaj", tab == CarTab.SEARCH) { go(CarTab.SEARCH) }
            RailItem(Icons.Default.LibraryMusic, "Biblioteka", tab == CarTab.LIBRARY) { go(CarTab.LIBRARY) }
            RailItem(Icons.Default.PlayCircle, "Odtwarzacz", tab == CarTab.PLAYER, enabled = vm.now != null) { go(CarTab.PLAYER) }
            RailItem(Icons.AutoMirrored.Filled.QueueMusic, "Kolejka", tab == CarTab.QUEUE, enabled = vm.queue.isNotEmpty()) { go(CarTab.QUEUE) }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        CarTab.PLAYER -> CarPlayer(vm)
                        CarTab.QUEUE -> QueueList(vm, Modifier.fillMaxSize().padding(top = 8.dp))
                        else -> when {
                            vm.currentCollection != null -> CollectionScreen(vm)
                            tab == CarTab.LIBRARY -> LibraryScreen(vm)
                            else -> CarSearch(vm, input)
                        }
                    }
                }
                if (tab != CarTab.PLAYER && vm.now != null) CarMiniBar(vm) { go(CarTab.PLAYER) }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        icon = { Icon(icon, null, Modifier.size(28.dp)) },
        label = { Text(label) },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

// ---------------------------------------------------------------- wyszukiwanie

@Composable
private fun CarSearch(vm: MainViewModel, input: CarInput) {
    Column(Modifier.fillMaxSize()) {
        // „Pole” wyszukiwania — po stuknięciu otwiera klawiaturę Android Auto.
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable {
                    input.start(vm.query, onChange = { vm.query = it }, onSubmit = { vm.search() })
                }
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    vm.query.ifEmpty { "Stuknij, aby wyszukać utwór, klip lub playlistę" },
                    color = if (vm.query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.search() }) {
                        Icon(Icons.Default.Search, "Szukaj", tint = Accent)
                    }
                }
            }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CarChip("Utwory", Icons.Default.MusicNote, vm.kind == SearchKind.SONGS) { vm.changeKind(SearchKind.SONGS) }
            CarChip("Klipy", Icons.Default.SmartDisplay, vm.kind == SearchKind.CLIPS) { vm.changeKind(SearchKind.CLIPS) }
            CarChip("Playlisty", Icons.AutoMirrored.Filled.PlaylistPlay, vm.kind == SearchKind.PLAYLISTS) { vm.changeKind(SearchKind.PLAYLISTS) }
        }

        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        val empty = when (vm.kind) {
            SearchKind.SONGS -> vm.songs.isEmpty()
            SearchKind.CLIPS -> vm.clips.isEmpty()
            SearchKind.PLAYLISTS -> vm.playlists.isEmpty()
        }
        if (empty && !vm.loading) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Wyszukaj coś albo otwórz Bibliotekę.\nUlubione i wykonawcy są wspólni z telefonem.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else when (vm.kind) {
            SearchKind.SONGS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(vm.songs, key = { i, t -> "$i-${t.url}" }) { i, t ->
                    TrackRow(t, playing = vm.now?.mediaId == t.url, onClick = { vm.playFromResults(i) },
                        trailing = { TrackActions(vm, t) })
                }
            }
            SearchKind.CLIPS -> LazyVerticalGrid(
                columns = GridCells.Adaptive(260.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 12.dp)
            ) {
                itemsIndexed(vm.clips, key = { i, t -> "$i-${t.url}" }) { i, t ->
                    ClipCard(t, playing = vm.now?.mediaId == t.url, onClick = { vm.playClip(i) },
                        trailing = { TrackActions(vm, t) })
                }
            }
            SearchKind.PLAYLISTS -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(vm.playlists, key = { i, p -> "$i-${p.url}" }) { _, p ->
                    PlaylistRow(p) { vm.showPlaylist(p) }
                }
            }
        }
    }
}

@Composable
private fun CarChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) }
    )
}

// ---------------------------------------------------------------- pasek „teraz gra”

@Composable
private fun CarMiniBar(vm: MainViewModel, onOpen: () -> Unit) {
    val now = vm.now ?: return
    val position by rememberPosition(vm.controller)
    val fraction = if (vm.durationMs > 0) (position.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
    Surface(color = Color(0xFF16161B)) {
        Column {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(now.art, Modifier.size(52.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(now.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BigIcon(Icons.Default.SkipPrevious, "Poprzedni") { vm.previous() }
                if (vm.isBuffering) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                } else {
                    BigIcon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Odtwórz/Pauza") { vm.togglePlay() }
                }
                BigIcon(Icons.Default.SkipNext, "Następny") { vm.next() }
            }
        }
    }
}

@Composable
private fun BigIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(icon, desc, Modifier.size(34.dp), tint = tint)
    }
}

// ---------------------------------------------------------------- odtwarzacz w aucie

@Composable
private fun CarPlayer(vm: MainViewModel) {
    val now = vm.now ?: return
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black, background = Color.Black)) {
        if (vm.videoMode) CarVideoPlayer(vm) else CarAudioPlayer(vm)
    }
}

/** Tryb audio: okładka z lewej, sterowanie z prawej. */
@Composable
private fun CarAudioPlayer(vm: MainViewModel) {
    val now = vm.now ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(Color(0xFF2A1520), Color(0xFF0E0E11))))
    ) {
        Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(now.art, Modifier.fillMaxHeight().aspectRatio(1f), corner = 16.dp)
            Spacer(Modifier.width(32.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeSwitch(video = vm.videoMode, onChange = { vm.changeVideoMode(it) })
                }
                Spacer(Modifier.height(16.dp))
                PlayerTitle(vm)
                Spacer(Modifier.height(12.dp))
                SeekBar(vm)
                Controls(vm)
                ExtraControls(vm)
            }
        }
    }
}

@Composable
private fun PlayerTitle(vm: MainViewModel) {
    val now = vm.now ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(now.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                now.artist,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = !now.uploaderUrl.isNullOrBlank()) { vm.openArtistNow() }
            )
        }
        val fav = vm.isFavorite(now.mediaId)
        IconButton(onClick = { vm.toggleFavoriteNow() }, modifier = Modifier.size(56.dp)) {
            Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Ulubione", Modifier.size(32.dp), tint = if (fav) Accent else Color.White)
        }
    }
}

/** Tryb wideo: obraz na cały ekran, sterowanie pojawia się po stuknięciu. */
@Composable
private fun CarVideoPlayer(vm: MainViewModel) {
    var controls by remember { mutableStateOf(true) }
    LaunchedEffect(controls, vm.isPlaying) {
        if (controls && vm.isPlaying) {
            delay(4000)
            controls = false
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = !controls
            }
    ) {
        VideoSurface(vm.controller, Modifier.fillMaxSize())

        AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { PlayerTitle(vm) }
                    Spacer(Modifier.width(12.dp))
                    ModeSwitch(video = vm.videoMode, onChange = { vm.changeVideoMode(it) })
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    SeekBar(vm)
                    Controls(vm)
                    ExtraControls(vm)
                }
            }
        }
    }
}
