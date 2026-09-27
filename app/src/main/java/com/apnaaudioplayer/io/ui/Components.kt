    @file:OptIn(ExperimentalTvMaterial3Api::class)

package com.apnaaudioplayer.io.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.apnaaudioplayer.io.AlbumArt
import com.apnaaudioplayer.io.R
import com.apnaaudioplayer.io.Track
import com.apnaaudioplayer.io.ui.theme.Accent
import com.apnaaudioplayer.io.ui.theme.OnAccent
import com.apnaaudioplayer.io.ui.theme.OnAccentMuted
import com.apnaaudioplayer.io.ui.theme.Surface1
import com.apnaaudioplayer.io.ui.theme.Surface2
import com.apnaaudioplayer.io.ui.theme.TextMuted
import com.apnaaudioplayer.io.ui.theme.TextPrimary

/** The design's focus treatment: a light ring floating just outside the control. */
private fun focusRing(shape: Shape) = Border(BorderStroke(2.5.dp, TextPrimary), inset = 4.dp, shape = shape)

/** Every focused control turns amber with dark content, so the remote's position is obvious. */
@Composable
private fun focusColors(container: Color, content: Color) = ClickableSurfaceDefaults.colors(
    containerColor = container,
    contentColor = content,
    focusedContainerColor = Accent,
    focusedContentColor = OnAccent,
    pressedContainerColor = Accent,
    pressedContentColor = OnAccent,
)

enum class ControlStyle { Primary, Standard, Quiet }

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    style: ControlStyle,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badge: String? = null,
) {
    val (container, content) = when (style) {
        ControlStyle.Primary -> Accent to OnAccent
        ControlStyle.Standard -> Surface2 to TextPrimary
        ControlStyle.Quiet -> Surface1 to if (active) Accent else TextMuted
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = focusColors(container, content),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRing(CircleShape)),
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize).align(Alignment.Center))
        if (badge != null) {
            Text(
                badge,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 9.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = if (primary) focusColors(Accent, OnAccent) else focusColors(Surface2, TextPrimary),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = focusRing(shape)),
    ) {
        Text(
            text,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 30.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** The cover embedded in [track]'s file, decoded for [sizePx]; null while loading or when it has none. */
@Composable
fun rememberAlbumArt(track: Track?, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    return produceState(track?.let { AlbumArt.cached(it, sizePx) }, track?.uri, sizePx) {
        value = track?.let { AlbumArt.load(context, it, sizePx) }
    }.value
}

/**
 * Album art for [track], falling back to the design's quiet tile with a music note while
 * it loads or when the file has no embedded cover.
 */
@Composable
fun ArtTile(
    size: Dp,
    corner: Dp,
    iconSize: Dp,
    iconTint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    track: Track? = null,
    artSizePx: Int = AlbumArt.SMALL_PX,
    placeholder: ImageVector = AppIcons.Music,
) {
    val art = rememberAlbumArt(track, artSizePx)
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(art, label = "album art") { image ->
            if (image != null) {
                Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(placeholder, contentDescription = null, modifier = Modifier.size(iconSize), tint = iconTint)
                }
            }
        }
    }
}

/** 30dp cover in list rows; the placeholder icon picks up the row's focus colors. */
@Composable
private fun RowThumb(track: Track?, placeholder: ImageVector, focused: Boolean, playing: Boolean) {
    ArtTile(
        size = 30.dp,
        corner = 6.dp,
        iconSize = 15.dp,
        iconTint = if (playing || focused) LocalContentColor.current else TextMuted,
        background = if (focused) OnAccent.copy(alpha = 0.12f) else Surface2,
        track = track,
        placeholder = placeholder,
    )
}

@Composable
fun ProgressLine(fraction: Float, height: Dp, track: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(height / 2)
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(shape).background(Accent))
    }
}

/** The shared 44dp list row: transparent at rest, amber when focused. [content] gets the focus state. */
@Composable
private fun BrowseRow(
    onClick: () -> Unit,
    contentColor: Color,
    modifier: Modifier,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = focusColors(Color.Transparent, contentColor),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) { content(focused) }
    }
}

private val rowText @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)

/**
 * One song in a list. Focused rows fill amber; the playing row shows an equalizer
 * glyph and amber text, matching the "Focus & states" board. [detail] is the second
 * column — the artist, or the album when browsing an artist.
 */
@Composable
fun SongRow(number: String, track: Track, detail: String, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BrowseRow(onClick, if (playing) Accent else TextPrimary, modifier) { focused ->
        val secondary = if (focused) OnAccentMuted else TextMuted
        Box(Modifier.width(24.dp)) {
            if (playing) {
                Icon(AppIcons.Equalizer, contentDescription = "Now playing", modifier = Modifier.size(16.dp))
            } else {
                Text(number, style = rowText, color = secondary)
            }
        }
        RowThumb(track, AppIcons.Music, focused, playing)
        Text(
            track.title,
            modifier = Modifier.weight(1f),
            style = rowText,
            fontWeight = if (focused || playing) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(detail, Modifier.width(180.dp), style = rowText, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            formatTime(track.durationMs),
            modifier = Modifier.width(48.dp),
            style = rowText.copy(fontFeatureSettings = "tnum"),
            color = secondary,
            textAlign = TextAlign.End,
        )
    }
}

/** An album, artist or folder. Turns amber like a playing song when it holds the current track. */
@Composable
fun GroupRow(
    icon: ImageVector,
    group: TrackGroup,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = false,
) {
    BrowseRow(onClick, if (playing) Accent else TextPrimary, modifier) { focused ->
        val secondary = if (focused) OnAccentMuted else TextMuted
        Box(contentAlignment = Alignment.Center) {
            // Albums show their cover (from the first song that has one); others show their icon.
            RowThumb(if (showArt) group.tracks.first() else null, if (playing) AppIcons.Equalizer else icon, focused, playing)
            if (playing && showArt && rememberAlbumArt(group.tracks.first(), AlbumArt.SMALL_PX) != null) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(OnAccent.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Equalizer, contentDescription = "Now playing", modifier = Modifier.size(15.dp), tint = Accent)
                }
            }
        }
        Text(
            group.title,
            modifier = Modifier.weight(1f),
            style = rowText,
            fontWeight = if (focused || playing) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(group.subtitle, Modifier.width(220.dp), style = rowText, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.song_count, group.tracks.size, group.tracks.size),
            modifier = Modifier.width(72.dp),
            style = rowText.copy(fontFeatureSettings = "tnum"),
            color = secondary,
            textAlign = TextAlign.End,
        )
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
