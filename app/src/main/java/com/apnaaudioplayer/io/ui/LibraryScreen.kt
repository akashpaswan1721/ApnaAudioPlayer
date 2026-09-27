@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.apnaaudioplayer.io.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.apnaaudioplayer.io.Player
import com.apnaaudioplayer.io.R
import com.apnaaudioplayer.io.Track
import com.apnaaudioplayer.io.ui.theme.Accent
import com.apnaaudioplayer.io.ui.theme.OnAccent
import com.apnaaudioplayer.io.ui.theme.Surface1
import com.apnaaudioplayer.io.ui.theme.Surface2
import com.apnaaudioplayer.io.ui.theme.Surface3
import com.apnaaudioplayer.io.ui.theme.TextFaint
import com.apnaaudioplayer.io.ui.theme.TextMuted
import com.apnaaudioplayer.io.ui.theme.TextPrimary

/** Which tab is open, and which album/artist/folder inside it. Survives trips to Now Playing. */
class LibraryState {
    var section by mutableStateOf(Section.Songs)
    var openGroupKey by mutableStateOf<String?>(null)
}

/** Board "2 · Library — Songs", extended to the Albums, Artists and Folders tabs in its sidebar. */
@Composable
fun LibraryScreen(
    tracks: List<Track>,
    player: Player,
    state: LibraryState,
    onPlay: (queue: List<Track>, index: Int) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val res = LocalContext.current.resources
    val groups = remember(tracks, state.section) { groupTracks(state.section, tracks, res) }
    val openGroup = groups.find { it.key == state.openGroupKey }
    val currentUri = player.current?.uri

    // Bumping focusToken moves focus into the list: onto preferredKey if set, else the playing row.
    var focusToken by remember { mutableIntStateOf(1) }
    var preferredKey by remember { mutableStateOf<String?>(null) }
    fun refocus(key: String? = null) {
        preferredKey = key
        focusToken++
    }

    BackHandler(enabled = openGroup != null) {
        val key = state.openGroupKey
        state.openGroupKey = null
        refocus(key)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.music), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(AppIcons.Storage, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextMuted)
            Text(
                stringResource(R.string.storage_sources),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SectionNav(
                selected = state.section,
                onSelect = { section ->
                    state.section = section
                    state.openGroupKey = null
                    refocus()
                },
                modifier = Modifier.width(141.dp),
            )
            Column(Modifier.weight(1f)) {
                when {
                    state.section == Section.Songs -> {
                        ListHeader(
                            title = stringResource(R.string.songs),
                            detail = "${tracks.size} · ${stringResource(R.string.sort_label)}",
                        )
                        SongList(tracks, Section.Songs, currentUri, onPlay, focusToken, preferredKey, Modifier.weight(1f))
                    }
                    openGroup != null -> {
                        ListHeader(
                            eyebrow = stringResource(state.section.title),
                            title = openGroup.title,
                            detail = listOf(
                                openGroup.subtitle,
                                pluralStringResource(R.plurals.song_count, openGroup.tracks.size, openGroup.tracks.size),
                            ).filter { it.isNotEmpty() }.joinToString(" · "),
                        )
                        SongList(openGroup.tracks, state.section, currentUri, onPlay, focusToken, preferredKey, Modifier.weight(1f))
                    }
                    else -> {
                        ListHeader(
                            title = stringResource(state.section.title),
                            detail = "${groups.size} · ${stringResource(R.string.sort_label_name)}",
                        )
                        GroupList(
                            groups = groups,
                            section = state.section,
                            currentUri = currentUri,
                            onOpen = { group ->
                                state.openGroupKey = group.key
                                refocus()
                            },
                            focusToken = focusToken,
                            preferredKey = preferredKey,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        player.current?.let { MiniPlayer(it, player, onOpenNowPlaying) }
    }
}

/**
 * The design's section rail. Selecting a tab moves focus straight into its list, and
 * pressing ◀ from the list always lands on the selected tab rather than the nearest one.
 */
@Composable
private fun SectionNav(selected: Section, onSelect: (Section) -> Unit, modifier: Modifier) {
    val selectedTab = remember { FocusRequester() }
    Column(
        modifier
            .focusProperties { onEnter = { selectedTab.requestFocus() } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Section.entries.forEach { section ->
            NavItem(
                stringResource(section.title),
                selected = section == selected,
                onClick = { onSelect(section) },
                modifier = if (section == selected) Modifier.focusRequester(selectedTab) else Modifier,
            )
        }
    }
}

@Composable
private fun NavItem(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(39.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Surface2 else Color.Transparent,
            contentColor = if (selected) TextPrimary else TextMuted,
            focusedContainerColor = Accent,
            focusedContentColor = OnAccent,
            pressedContainerColor = Accent,
            pressedContentColor = OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier.size(5.dp).clip(CircleShape)
                    .background(if (!selected) Color.Transparent else if (focused) OnAccent else Accent)
            )
            Text(title, fontSize = 14.sp, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ListHeader(title: String, detail: String, eyebrow: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 6.dp)) {
        if (eyebrow != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(AppIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(12.dp), tint = TextFaint)
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                modifier = Modifier.padding(start = 18.dp),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ColumnHeaders(
    first: String,
    firstWidth: Dp,
    title: String,
    detail: String,
    detailWidth: Dp,
    end: String,
    endWidth: Dp,
    thumbColumn: Boolean = false,
) {
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.75.sp, fontWeight = FontWeight.Normal)
    Row(
        Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(first, Modifier.width(firstWidth), style = style, color = TextFaint)
        if (thumbColumn) Spacer(Modifier.width(30.dp))
        Text(title.uppercase(), Modifier.weight(1f), style = style, color = TextFaint)
        Text(detail.uppercase(), Modifier.width(detailWidth), style = style, color = TextFaint)
        Text(end.uppercase(), Modifier.width(endWidth), style = style, color = TextFaint, textAlign = TextAlign.End)
    }
}

/** Songs of the whole library or of one group; the list itself is the play queue. */
@Composable
private fun SongList(
    songs: List<Track>,
    section: Section,
    currentUri: Uri?,
    onPlay: (List<Track>, Int) -> Unit,
    focusToken: Int,
    preferredKey: String?,
    modifier: Modifier,
) {
    val unknownAlbum = stringResource(R.string.unknown_album)
    ColumnHeaders(
        first = stringResource(R.string.col_number), firstWidth = 24.dp,
        title = stringResource(R.string.col_title),
        detail = stringResource(if (section == Section.Artists) R.string.col_album else R.string.col_artist), detailWidth = 180.dp,
        end = stringResource(R.string.col_time), endWidth = 48.dp,
        thumbColumn = true,
    )
    // Key by the list's contents so each tab/group gets a fresh scroll position.
    key(songs) {
        BrowseList(
            items = songs,
            key = { it.uri.toString() },
            focusToken = focusToken,
            focusIndex = {
                songs.indexOfFirst { it.uri.toString() == preferredKey }.takeIf { it >= 0 }
                    ?: songs.indexOfFirst { it.uri == currentUri }.coerceAtLeast(0)
            },
            modifier = modifier,
        ) { index, track, rowModifier ->
            val number = if (section == Section.Albums && track.trackNumber > 0) track.trackNumber else index + 1
            SongRow(
                number = "%02d".format(number),
                track = track,
                detail = if (section == Section.Artists) track.album ?: unknownAlbum else track.artist,
                playing = track.uri == currentUri,
                onClick = { onPlay(songs, index) },
                modifier = rowModifier,
            )
        }
    }
}

@Composable
private fun GroupList(
    groups: List<TrackGroup>,
    section: Section,
    currentUri: Uri?,
    onOpen: (TrackGroup) -> Unit,
    focusToken: Int,
    preferredKey: String?,
    modifier: Modifier,
) {
    val (title, detail, icon) = when (section) {
        Section.Artists -> Triple(R.string.col_artist, R.string.albums, AppIcons.Person)
        Section.Folders -> Triple(R.string.col_folder, R.string.col_location, AppIcons.Folder)
        else -> Triple(R.string.col_album, R.string.col_artist, AppIcons.Disc)
    }
    ColumnHeaders(
        first = "", firstWidth = 30.dp,
        title = stringResource(title),
        detail = stringResource(detail), detailWidth = 220.dp,
        end = stringResource(R.string.col_songs), endWidth = 72.dp,
    )
    key(section, groups) {
        BrowseList(
            items = groups,
            key = { it.key },
            focusToken = focusToken,
            focusIndex = {
                groups.indexOfFirst { it.key == preferredKey }.takeIf { it >= 0 }
                    ?: groups.indexOfFirst { g -> g.tracks.any { it.uri == currentUri } }.coerceAtLeast(0)
            },
            modifier = modifier,
        ) { _, group, rowModifier ->
            GroupRow(
                icon = icon,
                group = group,
                playing = currentUri != null && group.tracks.any { it.uri == currentUri },
                onClick = { onOpen(group) },
                modifier = rowModifier,
                showArt = section == Section.Albums,
            )
        }
    }
}

/**
 * A lazy list that pulls focus onto one of its rows whenever [focusToken] changes — on
 * entering the screen, switching tabs, or opening/closing a group. [focusIndex] picks the row.
 */
@Composable
private fun <T> BrowseList(
    items: List<T>,
    key: (T) -> String,
    focusToken: Int,
    focusIndex: () -> Int,
    modifier: Modifier,
    row: @Composable (index: Int, item: T, modifier: Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val requester = remember { FocusRequester() }
    var target by remember { mutableIntStateOf(-1) }

    LaunchedEffect(focusToken) {
        if (items.isEmpty()) return@LaunchedEffect
        target = focusIndex().coerceIn(0, items.lastIndex)
        listState.scrollToItem(target)
        withFrameNanos { } // let the target row compose before focusing it
        runCatching { requester.requestFocus() }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            row(index, item, if (index == target) Modifier.focusRequester(requester) else Modifier)
        }
    }
}

@Composable
private fun MiniPlayer(track: Track, player: Player, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Surface1,
            contentColor = TextPrimary,
            focusedContainerColor = Surface2,
            focusedContentColor = TextPrimary,
            pressedContainerColor = Surface2,
            pressedContentColor = TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 9.dp, end = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArtTile(size = 36.dp, corner = 7.dp, iconSize = 16.dp, iconTint = TextMuted, background = Surface3, track = track)
            Column(Modifier.width(180.dp)) {
                Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val fraction = if (track.durationMs > 0) player.positionMs.toFloat() / track.durationMs else 0f
            ProgressLine(fraction, height = 3.dp, track = Surface3, modifier = Modifier.weight(1f))
            Text(
                "${formatTime(player.positionMs)} / ${formatTime(track.durationMs)}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"),
                color = TextMuted,
            )
            Icon(
                if (player.isPlaying) AppIcons.Pause else AppIcons.Play,
                contentDescription = stringResource(if (player.isPlaying) R.string.pause else R.string.play),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
