package com.apnaaudioplayer.io

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioRepository {

    const val PRIMARY_VOLUME = "external_primary"

    private val baseProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DISPLAY_NAME,
    )

    // Android 10+ exposes each file's folder directly; older versions only give the full path.
    @Suppress("DEPRECATION")
    private val projection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) baseProjection + MediaStore.Audio.Media.RELATIVE_PATH
        else baseProjection + MediaStore.Audio.Media.DATA

    private const val SELECTION =
        "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')" +
            " AND ${MediaStore.Audio.Media.DURATION} > 0"

    /** Scans every mounted storage volume (internal + USB) for local audio files. */
    suspend fun loadTracks(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val tracks = LinkedHashMap<Uri, Track>()
        for ((volume, collection) in collections(context)) {
            runCatching { query(context, volume, collection, tracks) }
        }
        tracks.values.sortedBy { it.title.lowercase() }
    }

    private fun collections(context: Context): List<Pair<String, Uri>> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).map { it to MediaStore.Audio.Media.getContentUri(it) }
        } else {
            listOf(PRIMARY_VOLUME to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

    private fun query(context: Context, volume: String, collection: Uri, out: MutableMap<Uri, Track>) {
        context.contentResolver.query(collection, projection, SELECTION, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val locationCol = c.getColumnIndexOrThrow(projection.last())
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val (trackVolume, folder) = location(volume, c.getString(locationCol))
                val artist = c.getString(artistCol)
                    ?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: "Unknown artist"
                out[uri] = Track(
                    id = id,
                    uri = uri,
                    title = c.getString(titleCol) ?: c.getString(nameCol) ?: "Unknown",
                    artist = artist,
                    album = c.getString(albumCol)?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                    // TRACK is encoded as disc * 1000 + track.
                    trackNumber = c.getInt(trackCol) % 1000,
                    durationMs = c.getLong(durCol),
                    mime = c.getString(mimeCol) ?: "audio/*",
                    volume = trackVolume,
                    folder = folder,
                )
            }
        }
    }

    /**
     * Turns the location column into (volume, folder). On Android 10+ it is already a
     * RELATIVE_PATH like `Music/Road Trip/`; before that it is a full file path such as
     * `/storage/emulated/0/Music/Road Trip/song.mp3` or `/storage/1A2B-3C4D/song.mp3`.
     */
    @Suppress("DEPRECATION")
    private fun location(volume: String, raw: String?): Pair<String, String> {
        if (raw == null) return volume to ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return volume to raw.trim('/')
        val dir = raw.substringBeforeLast('/', "")
        val primary = Environment.getExternalStorageDirectory().absolutePath
        return when {
            dir.startsWith(primary) -> PRIMARY_VOLUME to dir.removePrefix(primary).trim('/')
            dir.startsWith("/storage/") -> {
                val rest = dir.removePrefix("/storage/")
                rest.substringBefore('/') to rest.substringAfter('/', "")
            }
            else -> volume to dir.trim('/')
        }
    }
}
