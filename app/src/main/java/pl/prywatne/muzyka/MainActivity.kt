package pl.prywatne.muzyka

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                NotificationPermission()
                MainScreen(vm)
            }
        }
    }
}

@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Muzyka") },
                actions = {
                    IconButton(onClick = vm::checkUpdates) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = "Sprawdź aktualizacje")
                    }
                }
            )
        },
        bottomBar = { if (vm.nowPlaying != null) PlayerBar(vm) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Szukaj utworu lub wykonawcy") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() })
            )
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.results, key = { it.url }) { track ->
                    TrackRow(
                        track = track,
                        onPlay = { vm.play(track) },
                        onQueue = { vm.play(track, playNow = false) }
                    )
                }
            }
        }
    }

    vm.updateText?.let { text -> UpdateDialog(vm, text) }
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit, onQueue: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(track.uploader, track.durationSec.asDuration()).filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onQueue) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Dodaj do kolejki")
        }
    }
}

@Composable
private fun PlayerBar(vm: MainViewModel) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(vm.nowPlaying.orEmpty(), Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { vm.previous() }) { Icon(Icons.Default.SkipPrevious, "Poprzedni") }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Odtwórz/Pauza")
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Następny") }
        }
    }
}

@Composable
private fun UpdateDialog(vm: MainViewModel, text: String) {
    val progress = vm.downloadProgress
    AlertDialog(
        onDismissRequest = { if (progress == null) vm.updateText = null },
        title = { Text("Aktualizacje") },
        text = {
            Column {
                Text(text)
                if (progress != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth())
                    Text("$progress%")
                }
            }
        },
        confirmButton = {
            if (vm.pendingUpdate != null) {
                Button(onClick = vm::installUpdate, enabled = progress == null) {
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
