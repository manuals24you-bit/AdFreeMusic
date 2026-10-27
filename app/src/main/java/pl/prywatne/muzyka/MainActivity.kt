package pl.prywatne.muzyka

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import pl.prywatne.muzyka.ui.AppTheme
import pl.prywatne.muzyka.ui.FullscreenVideo
import pl.prywatne.muzyka.ui.MiniPlayer
import pl.prywatne.muzyka.ui.PlayerScreen
import pl.prywatne.muzyka.ui.CollectionScreen
import pl.prywatne.muzyka.ui.LibraryScreen
import pl.prywatne.muzyka.ui.SearchScreen
import pl.prywatne.muzyka.ui.UpdateDialog

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                NotificationPermission()
                AppRoot(vm)
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

@Composable
private fun AppRoot(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    // Nie wygaszaj ekranu, gdy widać odtwarzane wideo.
    val view = LocalView.current
    val keepScreenOn = vm.videoMode && vm.isPlaying && (vm.playerOpen || vm.fullscreen)
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = vm.fullscreen) { vm.fullscreen = false }
    BackHandler(enabled = vm.playerOpen && !vm.fullscreen) { vm.playerOpen = false }
    BackHandler(enabled = vm.pages.isNotEmpty() && !vm.playerOpen && !vm.fullscreen) { vm.back() }
    BackHandler(enabled = vm.pages.isEmpty() && vm.tab == MainTab.LIBRARY && !vm.playerOpen && !vm.fullscreen) {
        vm.selectTab(MainTab.SEARCH)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    if (vm.now != null) MiniPlayer(vm)
                    NavigationBar {
                        NavigationBarItem(
                            selected = vm.tab == MainTab.SEARCH,
                            onClick = { vm.selectTab(MainTab.SEARCH) },
                            icon = { Icon(Icons.Default.Search, null) },
                            label = { Text("Szukaj") }
                        )
                        NavigationBarItem(
                            selected = vm.tab == MainTab.LIBRARY,
                            onClick = { vm.selectTab(MainTab.LIBRARY) },
                            icon = { Icon(Icons.Default.LibraryMusic, null) },
                            label = { Text("Biblioteka") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when {
                    vm.currentCollection != null -> CollectionScreen(vm)
                    vm.tab == MainTab.LIBRARY -> LibraryScreen(vm)
                    else -> SearchScreen(vm)
                }
            }
        }

        AnimatedVisibility(
            visible = vm.playerOpen && vm.now != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PlayerScreen(vm)
        }

        if (vm.fullscreen && vm.videoMode) FullscreenVideo(vm)
    }

    vm.updateText?.let { UpdateDialog(vm, it) }
}
