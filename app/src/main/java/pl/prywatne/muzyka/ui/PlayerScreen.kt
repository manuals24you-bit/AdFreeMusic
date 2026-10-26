@file:OptIn(ExperimentalMaterial3Api::class)

package pl.prywatne.muzyka.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.prywatne.muzyka.MainViewModel

private val PlayerBackground = Color(0xFF0E0E11)
private val Dim = Color.White.copy(alpha = 0.65f)

// ---------------------------------------------------------------- mini odtwarzacz

@Composable
fun MiniPlayer(vm: MainViewModel) {
    val now = vm.now ?: return
    val position by rememberPosition(vm.controller)
    val fraction = if (vm.durationMs > 0) (position.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f

    Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.playerOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(now.art, Modifier.size(44.dp), corner = 6.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        now.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        now.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vm.isBuffering) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = { vm.togglePlay() }) {
                        Icon(
                            if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Odtwórz/Pauza"
                        )
                    }
                }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Następny") }
            }
        }
    }
}

// ---------------------------------------------------------------- pełny ekran odtwarzania

@Composable
fun PlayerScreen(vm: MainViewModel) {
    val now = vm.now ?: return
    val ctx = LocalContext.current
    var showQueue by remember { mutableStateOf(false) }

    // Kolor tła dopasowany do okładki.
    var accent by remember { mutableStateOf(Color(0xFF2A2A33)) }
    LaunchedEffect(now.art) {
        val art = now.art ?: return@LaunchedEffect
        val request = ImageRequest.Builder(ctx).data(art).allowHardware(false).size(96).build()
        val bitmap = (ctx.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
            ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val c = palette.getVibrantColor(palette.getDominantColor(0xFF2A2A33.toInt()))
        accent = lerp(Color(c), Color.Black, 0.45f)
    }
    val background by animateColorAsState(accent, tween(700), label = "bg")

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            surface = Color(0xFF1A1A1F),
            background = PlayerBackground,
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(background, PlayerBackground)))
                // Pochłania dotyk, żeby nie przechodził do listy pod spodem.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.playerOpen = false }) {
                        Icon(Icons.Default.KeyboardArrowDown, "Zwiń", Modifier.size(32.dp), tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    ModeSwitch(video = vm.videoMode, onChange = { vm.changeVideoMode(it) })
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, "Kolejka", tint = Color.White)
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (vm.videoMode) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            if (!vm.fullscreen) VideoSurface(vm.controller, Modifier.fillMaxSize())
                            IconButton(
                                onClick = { vm.fullscreen = true },
                                modifier = Modifier.align(Alignment.BottomEnd)
                            ) {
                                Icon(Icons.Default.Fullscreen, "Pełny ekran", tint = Color.White)
                            }
                        }
                    } else {
                        val scale by animateFloatAsState(
                            if (vm.isPlaying) 1f else 0.88f, tween(400), label = "art"
                        )
                        Artwork(
                            now.art,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .shadow(24.dp, RoundedCornerShape(20.dp)),
                            corner = 20.dp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    now.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    now.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                SeekBar(vm)
                Controls(vm)
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showQueue) QueueSheet(vm) { showQueue = false }
    }
}

@Composable
private fun ModeSwitch(video: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(4.dp)
    ) {
        ModeTab("Audio", Icons.Default.MusicNote, selected = !video) { onChange(false) }
        ModeTab("Wideo", Icons.Default.Videocam, selected = video) { onChange(true) }
    }
}

@Composable
private fun ModeTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Color.Black else Color.White
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
private fun SeekBar(vm: MainViewModel) {
    val position by rememberPosition(vm.controller)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = vm.durationMs
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Slider(
        value = fraction,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { vm.seekTo((it * duration).toLong()) }
            dragging = null
        },
        enabled = duration > 0,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        )
    )
    Row(Modifier.fillMaxWidth()) {
        val shown = if (dragging != null) (fraction * duration).toLong() else position
        Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = Dim)
        Spacer(Modifier.weight(1f))
        Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = Dim)
    }
}

@Composable
private fun Controls(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vm.toggleShuffle() }) {
            Icon(Icons.Default.Shuffle, "Losowo", tint = if (vm.shuffle) Accent else Dim)
        }
        IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, "Poprzedni", Modifier.size(40.dp), tint = Color.White)
        }
        FilledIconButton(
            onClick = { vm.togglePlay() },
            modifier = Modifier.size(76.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            if (vm.isBuffering) {
                CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Odtwórz/Pauza",
                    Modifier.size(42.dp)
                )
            }
        }
        IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, "Następny", Modifier.size(40.dp), tint = Color.White)
        }
        IconButton(onClick = { vm.cycleRepeat() }) {
            Icon(
                if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                "Powtarzanie",
                tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Dim else Accent
            )
        }
    }
}

@Composable
private fun QueueSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A1F)) {
        Text(
            "Kolejka · ${vm.queue.size}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.currentIndex.coerceAtLeast(0))
        LazyColumn(Modifier.fillMaxHeight(0.75f), state = listState) {
            itemsIndexed(vm.queue) { i, entry ->
                val current = i == vm.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.jumpTo(i) }
                        .background(if (current) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp)) {
                        Artwork(entry.art, Modifier.fillMaxSize(), corner = 6.dp)
                        if (current) {
                            Box(
                                Modifier.matchParentSize().clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.GraphicEq, null, tint = Accent) }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (current) Accent else Color.White,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = Dim
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- wideo

@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        modifier = modifier,
        onRelease = { it.player = null },
        update = { it.player = player }
    )
}

@Composable
fun FullscreenVideo(vm: MainViewModel) {
    val activity = LocalContext.current as? Activity
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                showControls = !showControls
            }
    ) {
        VideoSurface(vm.controller, Modifier.fillMaxSize())
        if (showControls) {
            IconButton(
                onClick = { vm.fullscreen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.FullscreenExit, "Zamknij pełny ekran", tint = Color.White)
            }
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Poprzedni", Modifier.size(40.dp), tint = Color.White)
                }
                IconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Odtwórz/Pauza", Modifier.size(56.dp), tint = Color.White
                    )
                }
                IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Następny", Modifier.size(40.dp), tint = Color.White)
                }
            }
        }
    }
}
