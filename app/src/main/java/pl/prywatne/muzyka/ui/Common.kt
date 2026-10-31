@file:OptIn(ExperimentalMaterial3Api::class)

package pl.prywatne.muzyka.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import pl.prywatne.muzyka.ArtistRef
import pl.prywatne.muzyka.BuildConfig
import pl.prywatne.muzyka.MainViewModel
import pl.prywatne.muzyka.PlaylistRef
import pl.prywatne.muzyka.Track
import pl.prywatne.muzyka.asDuration

val Accent = Color(0xFFFF4F79)

/** true, gdy interfejs jest wyświetlany na ekranie w aucie (Android Auto). */
val LocalCarMode = staticCompositionLocalOf { false }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Accent, secondary = Accent)
        else -> lightColorScheme(primary = Color(0xFFD81B60), secondary = Color(0xFFD81B60))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Okładka z zaokrąglonymi rogami i ikoną nutki, dopóki obrazek się nie wczyta. */
@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
    }
}

/** Aktualna pozycja odtwarzania, odświeżana dwa razy na sekundę. */
@Composable
fun rememberPosition(player: Player?): State<Long> {
    val state = remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            state.longValue = player?.currentPosition ?: 0L
            delay(500)
        }
    }
    return state
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun TrackRow(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    index: Int? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Text(
                "${index + 1}",
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(Modifier.size(56.dp)) {
            Artwork(track.thumbnail, Modifier.fillMaxSize())
            if (playing) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                listOf(track.uploader, track.durationSec.asDuration())
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

/** Duża karta filmu (zakładka Klipy): miniatura 16:9 z czasem trwania, tytuł i kanał. */
@Composable
fun ClipCard(clip: Track, playing: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Artwork(clip.thumbnail, Modifier.fillMaxSize(), corner = 14.dp)
            if (playing) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, null, Modifier.size(48.dp), tint = Color.White)
                }
            }
            val duration = clip.durationSec.asDuration()
            if (duration.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        duration,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    clip.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    clip.uploader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

/** Serduszko (ulubione) + menu: dodaj do kolejki, przejdź do wykonawcy. */
@Composable
fun TrackActions(vm: MainViewModel, track: Track, showArtist: Boolean = true) {
    val favorite = vm.isFavorite(track.url)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.toggleFavorite(track) }) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (favorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                tint = if (favorite) Accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (LocalCarMode.current) {
            // W aucie nie ma okienek (menu) — przyciski są widoczne od razu.
            IconButton(onClick = { vm.enqueue(track) }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Dodaj do kolejki",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showArtist && !track.uploaderUrl.isNullOrBlank()) {
                IconButton(onClick = { vm.openArtistOf(track) }) {
                    Icon(Icons.Default.Person, "Wykonawca", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else Box {
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, "Więcej", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Dodaj do kolejki") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = {
                        menu = false
                        vm.enqueue(track)
                    }
                )
                if (showArtist && !track.uploaderUrl.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Wykonawca: ${track.uploader}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        onClick = {
                            menu = false
                            vm.openArtistOf(track)
                        }
                    )
                }
            }
        }
    }
}

/** Okrągły awatar wykonawcy z podpisem (Biblioteka → Zapisani wykonawcy). */
@Composable
fun ArtistBubble(artist: ArtistRef, onClick: () -> Unit) {
    Column(
        Modifier
            .width(88.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(
                model = artist.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            artist.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun PlaylistRow(playlist: PlaylistRef, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f)) {
            Artwork(playlist.thumbnail, Modifier.fillMaxSize())
            if (playlist.count > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                ) {
                    Text(
                        "${playlist.count} utw.",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                playlist.uploader,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Okno „O aplikacji”: nazwa, wersja, twórca, źródła i (opcjonalnie) link do dotacji. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val donateUrl = BuildConfig.DONATE_URL
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.MusicNote, null, tint = Accent) },
        title = { Text("Hoodify") },
        text = {
            Column {
                Text("Twórca: Piotr Bogacz", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Odtwarzacz muzyki i filmów z YouTube bez reklam, z odtwarzaniem w tle. " +
                        "Ulubione i zapisani wykonawcy są przechowywane tylko na tym telefonie.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Korzysta z NewPipeExtractor ${BuildConfig.EXTRACTOR_VERSION} (licencja GPLv3). " +
                        "Aplikacja nie jest powiązana z YouTube ani Google.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (donateUrl.isNotBlank()) {
                Button(onClick = { runCatching { uriHandler.openUri(donateUrl) } }) {
                    Icon(Icons.Default.Favorite, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Wesprzyj twórcę")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (donateUrl.isNotBlank()) TextButton(onClick = onDismiss) { Text("Zamknij") }
        }
    )
}

@Composable
fun UpdateDialog(vm: MainViewModel, text: String) {
    val progress = vm.downloadProgress
    AlertDialog(
        onDismissRequest = { if (progress == null) vm.updateText = null },
        title = { Text("Aktualizacje") },
        text = {
            Column {
                Text(text)
                if (progress != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("$progress%")
                }
            }
        },
        confirmButton = {
            if (vm.pendingUpdate != null) {
                Button(onClick = { vm.installUpdate() }, enabled = progress == null) {
                    Text("Pobierz i zainstaluj")
                }
            } else {
                TextButton(onClick = { vm.updateText = null }) { Text("OK") }
            }
        },
        dismissButton = {
            if (vm.pendingUpdate != null && progress == null) {
                TextButton(onClick = { vm.updateText = null }) { Text("Później") }
            }
        }
    )
}
