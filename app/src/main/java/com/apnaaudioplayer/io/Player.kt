package com.apnaaudioplayer.io

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Tiny wrapper around the platform [MediaPlayer] exposing Compose state. One instance lives
 * for the whole app (see [ApnaApp]) so playback carries on when the screen goes away;
 * [PlaybackService] listens to it to keep the media session and notification in sync.
 */
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

    /** The exact playback position right now, unlike [positionMs] which the UI refreshes on a timer. */
    val livePositionMs: Long get() = mp?.takeIf { prepared }?.currentPosition?.toLong() ?: positionMs

    private var mp: MediaPlayer? = null
    private var prepared = false

    /** Playback order as indices into [tracks]; shuffled when [shuffle] is on. */
    private var order: List<Int> = emptyList()

    private val listeners = mutableListOf<() -> Unit>()

    /** Called whenever the track, play/pause state or position jumps (not on every tick). */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun changed() = listeners.toList().forEach { it() }

    // --- Audio focus: pause when another app starts playing, duck under short sounds. ---

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Set when focus was lost only briefly (e.g. a notification sound) and we were playing. */
    private var resumeOnFocusGain = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                mp?.setVolume(1f, 1f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resume()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp?.setVolume(0.2f, 0.2f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = isPlaying
                pauseInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
        }
    }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
        } else {
            null
        }

    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        resumeOnFocusGain = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    // --- Queue ---

    /** Replaces the playlist, keeping the current song selected if it still exists. */
    fun updateTracks(newTracks: List<Track>) {
        val playingUri = current?.uri
        tracks = newTracks
        currentIndex = newTracks.indexOfFirst { it.uri == playingUri }
        if (currentIndex < 0) release()
        rebuildOrder()
        changed()
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
            setAudioAttributes(audioAttributes)
            // Keeps the CPU awake while playing, so music doesn't stop when the screen sleeps.
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            setOnPreparedListener {
                prepared = true
                if (requestFocus()) {
                    it.start()
                    this@Player.isPlaying = true
                }
                changed()
            }
            setOnCompletionListener { onCompletion() }
            setOnErrorListener { _, _, _ ->
                this@Player.isPlaying = false
                // Skip unplayable files, but don't loop forever if nothing plays.
                if (tracks.size > 1) next() else changed()
                true
            }
            try {
                setDataSource(context, tracks[i].uri)
                prepareAsync()
            } catch (e: Exception) {
                this@Player.isPlaying = false
            }
        }
        changed()
    }

    fun toggle() {
        if (isPlaying) pause() else resume()
    }

    /** Starts or continues playback; loads the current (or first) song if nothing is loaded. */
    fun resume() {
        val p = mp
        if (p == null) {
            // Nothing loaded yet, or playback stopped at the end of the queue.
            if (tracks.isNotEmpty()) play(maxOf(currentIndex, 0))
            return
        }
        if (!prepared || p.isPlaying || !requestFocus()) return
        p.start()
        isPlaying = true
        changed()
    }

    /** Pauses at the user's request, and gives audio focus back to other apps. */
    fun pause() {
        pauseInternal()
        abandonFocus()
    }

    /** Pauses but keeps audio focus, so a brief interruption can hand it back to us. */
    private fun pauseInternal() {
        mp?.takeIf { prepared && it.isPlaying }?.pause()
        positionMs = livePositionMs
        isPlaying = false
        changed()
    }

    fun next() = play(step(+1))

    fun prev() {
        // Like most players: restart the song if we're more than 3s in.
        if (livePositionMs > 3_000) seekTo(0) else play(step(-1))
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

    fun seekBy(deltaMs: Long) = seekTo(livePositionMs + deltaMs)

    fun seekTo(ms: Long) {
        val p = mp?.takeIf { prepared } ?: return
        val target = ms.coerceIn(0, p.duration.toLong())
        p.seekTo(target.toInt())
        positionMs = target
        changed()
    }

    /** Called periodically from the UI to refresh [positionMs]. */
    fun tick() {
        mp?.takeIf { prepared }?.let { positionMs = it.currentPosition.toLong() }
    }

    /** Stops playback and frees the decoder. The queue is kept, so play starts it again. */
    fun release() {
        mp?.release()
        mp = null
        prepared = false
        isPlaying = false
        abandonFocus()
        changed()
    }
}
