@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.apnaaudioplayer.io

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import com.apnaaudioplayer.io.ui.EmptyScreen
import com.apnaaudioplayer.io.ui.LibraryScreen
import com.apnaaudioplayer.io.ui.LibraryState
import com.apnaaudioplayer.io.ui.LoadingScreen
import com.apnaaudioplayer.io.ui.NowPlayingScreen
import com.apnaaudioplayer.io.ui.PermissionScreen
import com.apnaaudioplayer.io.ui.theme.ApnaAudioPlayerTheme
import com.apnaaudioplayer.io.ui.theme.Background
import com.apnaaudioplayer.io.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

class MainActivity : ComponentActivity() {

    private val player get() = (application as ApnaApp).player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApnaAudioPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = NonInteractiveSurfaceDefaults.colors(containerColor = Background, contentColor = TextPrimary),
                ) {
                    PlayerApp(player)
                }
            }
        }
    }

    // Remote media keys work no matter which view has focus.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> player.toggle()
                KeyEvent.KEYCODE_MEDIA_NEXT -> player.next()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> player.prev()
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> player.seekBy(10_000)
                KeyEvent.KEYCODE_MEDIA_REWIND -> player.seekBy(-10_000)
                else -> return super.dispatchKeyEvent(event)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

private enum class Screen { Library, NowPlaying }

@Composable
private fun PlayerApp(player: Player) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, AUDIO_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    var screen by remember { mutableStateOf(Screen.Library) }
    val library = remember { LibraryState() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    fun scan() {
        tracks = null
        scope.launch {
            val found = AudioRepository.loadTracks(context)
            // Leave the queue alone if music is already going (e.g. the app was reopened
            // mid-album); otherwise the whole library becomes the queue for the play key.
            if (player.current == null) player.updateTracks(found)
            tracks = found
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) scan()
    }

    // Refresh the progress bar while playing.
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            player.tick()
            delay(500)
        }
    }

    BackHandler(enabled = screen == Screen.NowPlaying) { screen = Screen.Library }

    val list = tracks
    val current = player.current
    when {
        !hasPermission -> PermissionScreen(
            onAllow = { launcher.launch(AUDIO_PERMISSION) },
            onNotNow = { (context as? Activity)?.finish() },
        )
        list == null -> LoadingScreen()
        list.isEmpty() -> EmptyScreen(onScanAgain = ::scan)
        screen == Screen.NowPlaying && current != null ->
            NowPlayingScreen(current, player, onBack = { screen = Screen.Library })
        else -> LibraryScreen(
            tracks = list,
            player = player,
            state = library,
            onPlay = { queue, index ->
                // Picking the song that's already playing just opens it, without restarting.
                if (queue[index].uri != current?.uri) player.playFrom(queue, index)
                screen = Screen.NowPlaying
            },
            onOpenNowPlaying = { screen = Screen.NowPlaying },
        )
    }
}
