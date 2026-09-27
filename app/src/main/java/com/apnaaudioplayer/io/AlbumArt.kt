package com.apnaaudioplayer.io

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads album art for a track: the cover embedded in the audio file, or failing that a
 * `cover.jpg`-style image saved in the same folder. Art is decoded no larger than needed
 * and kept in a small memory cache so scrolling a long list doesn't decode it twice.
 */
object AlbumArt {

    /** Row thumbnails and the mini-player. */
    const val SMALL_PX = 160
    /** The Now Playing cover. */
    const val LARGE_PX = 640

    // A few decodes at a time keeps a fast scroll from flooding the disk (USB drives are slow).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val decoder = Dispatchers.IO.limitedParallelism(3)

    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt().coerceAtMost(48 * 1024)
    ) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
    }

    /** Files already checked and found to have no art, so they aren't read again. */
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Folder covers by folder, shared by every song in it; an absent entry means "not looked yet". */
    private val folderCovers = ConcurrentHashMap<String, Result<ImageBitmap?>>()

    /** Image names treated as a folder's cover, most common first. */
    private val coverNames = listOf("cover", "folder", "front", "album", "albumart")
        .flatMap { name -> listOf("jpg", "jpeg", "png", "webp").map { "$name.$it" } }

    private fun key(track: Track, sizePx: Int) = "${track.uri}@$sizePx"

    fun cached(track: Track, sizePx: Int): ImageBitmap? = cache.get(key(track, sizePx))

    suspend fun load(context: Context, track: Track, sizePx: Int): ImageBitmap? {
        val key = key(track, sizePx)
        cache.get(key)?.let { return it }
        if (key in missing) return null
        val art = withContext(decoder) { decode(context, track, sizePx)?.asImageBitmap() }
            ?: folderCover(context, track, sizePx)
        if (art != null) cache.put(key, art) else missing += key
        return art
    }

    private suspend fun folderCover(context: Context, track: Track, sizePx: Int): ImageBitmap? {
        val key = "${track.volume}/${track.folder}@$sizePx"
        folderCovers[key]?.let { return it.getOrNull() }
        val cover = withContext(decoder) {
            runCatching { findFolderCover(context, track, sizePx)?.asImageBitmap() }
        }
        folderCovers[key] = cover
        return cover.getOrNull()
    }

    /**
     * On Android 10+ the system's audio thumbnail (see [decode]) already falls back to
     * cover/folder/front/albumart images in the song's folder, so this mostly matters for
     * older TVs; on 10–12 the MediaStore query below is a backup for other names.
     */
    private fun findFolderCover(context: Context, track: Track, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val image = folderImageUri(context, track) ?: return null
            return context.contentResolver.loadThumbnail(image, Size(sizePx, sizePx), null)
        }
        val dir = folderPath(track)
        val file = coverNames.map { File(dir, it) }.firstOrNull { it.isFile }
            // Names on USB drives are often upper-case (COVER.JPG); fall back to a case-insensitive match.
            ?: dir.listFiles()?.let { files -> coverNames.firstNotNullOfOrNull { n -> files.find { it.name.equals(n, ignoreCase = true) } } }
            ?: return null
        return decodeSampled(sizePx) { opts -> BitmapFactory.decodeFile(file.path, opts) }
    }

    /**
     * Android 10+: find the cover through MediaStore, which indexes images in music folders
     * too. From Android 13 the audio permission no longer covers images, so the query comes
     * back empty there — fine, since the system thumbnail already found any cover.
     */
    private fun folderImageUri(context: Context, track: Track): Uri? {
        val images = MediaStore.Images.Media.getContentUri(track.volume)
        val folder = if (track.folder.isEmpty()) "" else "${track.folder}/"
        val placeholders = coverNames.joinToString(",") { "?" }
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
            "LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) IN ($placeholders)"
        val args = arrayOf(folder) + coverNames
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        return context.contentResolver.query(images, projection, selection, args, null)?.use { c ->
            val found = buildMap {
                while (c.moveToNext()) put(c.getString(1).lowercase(), c.getLong(0))
            }
            coverNames.firstNotNullOfOrNull { found[it] }?.let { ContentUris.withAppendedId(images, it) }
        }
    }

    /** Pre-Android 10: rebuild the folder's path from the volume and relative folder. */
    @Suppress("DEPRECATION")
    private fun folderPath(track: Track): File {
        val root = if (track.volume == AudioRepository.PRIMARY_VOLUME) Environment.getExternalStorageDirectory()
        else File("/storage", track.volume)
        return if (track.folder.isEmpty()) root else File(root, track.folder)
    }

    private fun decode(context: Context, track: Track, sizePx: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // MediaStore reads the embedded picture and caches its own thumbnail on disk.
            context.contentResolver.loadThumbnail(track.uri, Size(sizePx, sizePx), null)
        } else {
            embeddedPicture(context, track, sizePx)
        }
    }.getOrNull()

    private fun embeddedPicture(context: Context, track: Track, sizePx: Int): Bitmap? {
        val bytes = MediaMetadataRetriever().run {
            try {
                setDataSource(context, track.uri)
                embeddedPicture
            } finally {
                release()
            }
        } ?: return null
        return decodeSampled(sizePx) { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
    }

    /** Decodes at the largest power-of-two downscale that still covers [sizePx]. */
    private fun decodeSampled(sizePx: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
