package com.apnaaudioplayer.io

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class RepeatMode { OFF, ALL, ONE }

/** Tiny wrapper around the platform [MediaPlayer] exposing Compose state. */
class Player(private val context: Context) {

    var tracks: List<Track> = emptyList()
        private set

    var currentIndex by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(RepeatMode.ALL)
        private set

    val current: Track? get() = tracks.getOrNull(currentIndex)

    private var mp: MediaPlayer? = null
    private var prepared = false

    /** Playback order as indices into [tracks]; shuffled when [shuffle] is on. */
    private var order: List<Int> = emptyList()

    /** Replaces the playlist, keeping the current song selected if it still exists. */
    fun updateTracks(newTracks: List<Track>) {
        val playingUri = current?.uri
        tracks = newTracks
        currentIndex = newTracks.indexOfFirst { it.uri == playingUri }
        if (currentIndex < 0) release()
        rebuildOrder()
    }

    /** Makes [queue] the playlist (an album, artist, folder or the whole library) and plays [index]. */
    fun playFrom(queue: List<Track>, index: Int) {
        tracks = queue
        currentIndex = index
        rebuildOrder()
        play(index)
    }

    fun play(index: Int) {
        if (tracks.isEmpty()) return
        val i = Math.floorMod(index, tracks.size)
        currentIndex = i
        positionMs = 0
        prepared = false
        mp?.release()
        mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener {
                prepared = true
                it.start()
                this@Player.isPlaying = true
            }
            setOnCompletionListener { onCompletion() }
            setOnErrorListener { _, _, _ ->
                this@Player.isPlaying = false
                // Skip unplayable files, but don't loop forever if nothing plays.
                if (tracks.size > 1) next()
                true
            }
            try {
                setDataSource(context, tracks[i].uri)
                prepareAsync()
            } catch (e: Exception) {
                this@Player.isPlaying = false
            }
        }
    }

    fun toggle() {
        val p = mp
        if (p == null) {
            // Nothing loaded yet, or playback stopped at the end of the queue.
            if (tracks.isNotEmpty()) play(maxOf(currentIndex, 0))
            return
        }
        if (!prepared) return
        if (p.isPlaying) p.pause() else p.start()
        isPlaying = p.isPlaying
    }

    fun pause() {
        mp?.takeIf { prepared && it.isPlaying }?.pause()
        isPlaying = false
    }

    fun next() = play(step(+1))

    fun prev() {
        // Like most players: restart the song if we're more than 3s in.
        if (positionMs > 3_000) seekTo(0) else play(step(-1))
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        rebuildOrder()
    }

    fun cycleRepeat() {
        repeatMode = RepeatMode.entries[(repeatMode.ordinal + 1) % RepeatMode.entries.size]
    }

    /** The next [count] tracks in playback order after the current one. */
    fun upNext(count: Int): List<Track> {
        if (order.size < 2) return emptyList()
        val pos = order.indexOf(currentIndex)
        return (1..minOf(count, order.size - 1)).map { tracks[order[Math.floorMod(pos + it, order.size)]] }
    }

    private fun onCompletion() {
        when {
            repeatMode == RepeatMode.ONE -> play(currentIndex)
            repeatMode == RepeatMode.OFF && order.indexOf(currentIndex) == order.lastIndex -> {
                isPlaying = false
                positionMs = 0
                release()
            }
            else -> next()
        }
    }

    /** Index of the track [delta] steps away from the current one in playback order. */
    private fun step(delta: Int): Int {
        if (order.isEmpty()) return 0
        val pos = order.indexOf(currentIndex)
        if (pos < 0) return order.first()
        return order[Math.floorMod(pos + delta, order.size)]
    }

    private fun rebuildOrder() {
        val all = tracks.indices.toList()
        order = if (!shuffle) all else {
            // Keep the current song first so shuffling doesn't jump away from it.
            val rest = all.filter { it != currentIndex }.shuffled()
            if (currentIndex >= 0) listOf(currentIndex) + rest else rest
        }
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    private fun seekTo(ms: Long) {
        val p = mp?.takeIf { prepared } ?: return
        val target = ms.coerceIn(0, p.duration.toLong())
        p.seekTo(target.toInt())
        positionMs = target
    }

    /** Called periodically from the UI to refresh [positionMs]. */
    fun tick() {
        mp?.takeIf { prepared }?.let { positionMs = it.currentPosition.toLong() }
    }

    fun release() {
        mp?.release()
        mp = null
        prepared = false
        isPlaying = false
    }
}
