package com.apnaaudioplayer.io.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.apnaaudioplayer.io.AudioRepository
import com.apnaaudioplayer.io.R
import com.apnaaudioplayer.io.Track

enum class Section(@StringRes val title: Int) {
    Songs(R.string.songs),
    Albums(R.string.albums),
    Artists(R.string.artists),
    Folders(R.string.folders),
}

/** An album, artist or folder: a named, ordered set of songs that plays as one queue. */
data class TrackGroup(val key: String, val title: String, val subtitle: String, val tracks: List<Track>)

private val albumOrder = compareBy<Track>({ it.trackNumber == 0 }, { it.trackNumber }, { it.title.lowercase() })

/** Groups [tracks] for a browse [section], A–Z by name with unknowns last. Songs has no groups. */
fun groupTracks(section: Section, tracks: List<Track>, res: Resources): List<TrackGroup> {
    val groups = when (section) {
        Section.Songs -> return emptyList()
        Section.Albums -> tracks.groupBy { it.album }.map { (album, songs) ->
            val artists = songs.map { it.artist }.distinct()
            TrackGroup(
                key = "album:$album",
                title = album ?: res.getString(R.string.unknown_album),
                subtitle = artists.singleOrNull() ?: res.getString(R.string.various_artists),
                tracks = songs.sortedWith(albumOrder),
            )
        }
        Section.Artists -> tracks.groupBy { it.artist }.map { (artist, songs) ->
            val albums = songs.mapNotNull { it.album }.distinct().size
            TrackGroup(
                key = "artist:$artist",
                title = artist,
                subtitle = if (albums > 0) res.getQuantityString(R.plurals.album_count, albums, albums) else "",
                tracks = songs.sortedWith(compareBy<Track> { it.album?.lowercase() ?: "￿" }.then(albumOrder)),
            )
        }
        Section.Folders -> tracks.groupBy { it.volume to it.folder }.map { (location, songs) ->
            val (volume, folder) = location
            val drive = res.getString(
                if (volume == AudioRepository.PRIMARY_VOLUME) R.string.internal_storage else R.string.usb_drive
            )
            TrackGroup(
                key = "folder:$volume/$folder",
                title = folder.substringAfterLast('/').ifEmpty { res.getString(R.string.storage_root) },
                subtitle = if (folder.isEmpty()) drive else "$drive · $folder",
                tracks = songs.sortedBy { it.title.lowercase() },
            )
        }
    }
    val unknownAlbum = res.getString(R.string.unknown_album)
    return groups.sortedWith(compareBy({ it.title == unknownAlbum }, { it.title.lowercase() }))
}
