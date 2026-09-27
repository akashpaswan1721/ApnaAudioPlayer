@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.apnaaudioplayer.io.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.apnaaudioplayer.io.AlbumArt
import com.apnaaudioplayer.io.Player
import com.apnaaudioplayer.io.R
import com.apnaaudioplayer.io.RepeatMode
import com.apnaaudioplayer.io.Track
import com.apnaaudioplayer.io.ui.theme.Accent
import com.apnaaudioplayer.io.ui.theme.IconMuted
import com.apnaaudioplayer.io.ui.theme.OnAccent
import com.apnaaudioplayer.io.ui.theme.Separator
import com.apnaaudioplayer.io.ui.theme.Surface1
import com.apnaaudioplayer.io.ui.theme.Surface2
import com.apnaaudioplayer.io.ui.theme.TextFaint
import com.apnaaudioplayer.io.ui.theme.TextMuted
import com.apnaaudioplayer.io.ui.theme.TextPrimary

private const val SEEK_STEP_MS = 10_000L

/** Board "3 · Now Playing". */
@Composable
fun NowPlayingScreen(track: Track, player: Player, onBack: () -> Unit) {
    val playPause = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playPause.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(21.dp),
    ) {
        BackPill(onBack)

        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            ArtTile(
                size = 285.dp, corner = 15.dp, iconSize = 72.dp, iconTint = IconMuted, background = Surface2,
                modifier = Modifier.shadow(30.dp, RoundedCornerShape(15.dp)),
                track = track,
                artSizePx = AlbumArt.LARGE_PX,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(21.dp)) {
                TrackInfo(track)
                SeekBar(player.positionMs, track.durationMs, onSeek = player::seekBy)
                Controls(player, Modifier.focusRequester(playPause))
            }
        }

        UpNext(player.upNext(2))
    }
}

@Composable
private fun BackPill(onBack: () -> Unit) {
    Surface(
        onClick = onBack,
        modifier = Modifier.height(33.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Surface1,
            contentColor = TextMuted,
            focusedContainerColor = Accent,
            focusedContentColor = OnAccent,
            pressedContainerColor = Accent,
            pressedContentColor = OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(AppIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(stringResource(R.string.library), fontSize = 13.sp)
        }
    }
}

@Composable
private fun TrackInfo(track: Track) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.now_playing).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
        )
        Text(
            track.title,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(track.artist, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val details = listOfNotNull(
            track.album,
            track.trackNumber.takeIf { it > 0 }?.let { stringResource(R.string.track_number, it) },
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), fontSize = 13.5.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Focusable progress bar: it thickens and grows a thumb when focused, and ◀ ▶ seek 10 s. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val description = stringResource(R.string.seek_bar)

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(18.dp)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (it.key) {
                        Key.DirectionLeft -> { onSeek(-SEEK_STEP_MS); true }
                        Key.DirectionRight -> { onSeek(SEEK_STEP_MS); true }
                        else -> false
                    }
                }
                .focusable()
                .semantics { contentDescription = description },
            contentAlignment = Alignment.CenterStart,
        ) {
            ProgressLine(fraction, height = if (focused) 7.5.dp else 4.5.dp, track = Surface2)
            if (focused) {
                Box(
                    Modifier.offset(x = maxWidth * fraction - 8.dp).size(16.dp)
                        .background(TextPrimary.copy(alpha = 0.25f), CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(TextPrimary)
                )
            }
        }
        val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = timeStyle, color = TextMuted)
            Text(formatTime(durationMs), style = timeStyle, color = TextMuted)
        }
    }
}

@Composable
private fun Controls(player: Player, playPauseModifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        RoundIconButton(
            AppIcons.Shuffle, stringResource(R.string.shuffle) + if (player.shuffle) ": on" else ": off",
            onClick = player::toggleShuffle, size = 45.dp, iconSize = 18.dp,
            style = ControlStyle.Quiet, active = player.shuffle,
        )
        RoundIconButton(
            AppIcons.Previous, stringResource(R.string.previous),
            onClick = player::prev, size = 51.dp, iconSize = 21.dp, style = ControlStyle.Standard,
        )
        RoundIconButton(
            if (player.isPlaying) AppIcons.Pause else AppIcons.Play,
            stringResource(if (player.isPlaying) R.string.pause else R.string.play),
            onClick = player::toggle, size = 69.dp, iconSize = 27.dp, style = ControlStyle.Primary,
            modifier = playPauseModifier,
        )
        RoundIconButton(
            AppIcons.Next, stringResource(R.string.next),
            onClick = player::next, size = 51.dp, iconSize = 21.dp, style = ControlStyle.Standard,
        )
        RoundIconButton(
            AppIcons.Repeat, stringResource(R.string.repeat) + ": " + player.repeatMode.name.lowercase(),
            onClick = player::cycleRepeat, size = 45.dp, iconSize = 18.dp,
            style = ControlStyle.Quiet, active = player.repeatMode != RepeatMode.OFF,
            badge = if (player.repeatMode == RepeatMode.ONE) "1" else null,
        )
    }
}

@Composable
private fun UpNext(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.up_next).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Normal),
            color = TextFaint,
        )
        tracks.forEachIndexed { i, t ->
            if (i > 0) Text("·", fontSize = 13.sp, color = Separator)
            Text(t.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, fontSize = 13.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
