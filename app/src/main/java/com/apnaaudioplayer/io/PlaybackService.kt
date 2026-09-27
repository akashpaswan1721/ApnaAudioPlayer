package com.apnaaudioplayer.io

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps music playing when the app isn't on screen. It runs as a foreground service while
 * something plays, and publishes a [MediaSession] so the remote's media keys, the TV's
 * system media controls and (on phones) the lock screen and notification all work.
 */
class PlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.apnaaudioplayer.io.TOGGLE"
        private const val ACTION_NEXT = "com.apnaaudioplayer.io.NEXT"
        private const val ACTION_PREVIOUS = "com.apnaaudioplayer.io.PREVIOUS"
        private const val ACTION_STOP = "com.apnaaudioplayer.io.STOP"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            // Only fails if Android refuses a background start; playback carries on regardless.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)) }
        }
    }

    private val player get() = (application as ApnaApp).player
    private val notifications get() = getSystemService(NotificationManager::class.java)

    private lateinit var session: MediaSession
    private val scope = MainScope()
    private var inForeground = false

    private var artTrack: Uri? = null
    private var art: Bitmap? = null
    private var artJob: Job? = null

    private val onPlayerChanged: () -> Unit = { update() }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        session = MediaSession(this, "ApnaAudioPlayer").apply {
            setCallback(SessionCallback())
            setSessionActivity(openAppIntent())
            isActive = true
        }
        player.addListener(onPlayerChanged)
        // startForegroundService() must be answered with startForeground() straight away.
        goForeground(buildNotification())
        update()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> player.toggle()
            ACTION_NEXT -> player.next()
            ACTION_PREVIOUS -> player.prev()
            ACTION_STOP -> {
                player.pause()
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away (on phones) ends a paused session; music that's playing keeps going.
        if (!player.isPlaying) stopEverything()
    }

    override fun onDestroy() {
        isRunning = false
        player.removeListener(onPlayerChanged)
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Mirrors the player into the session and notification, and moves in or out of the foreground. */
    private fun update() {
        val track = player.current
        if (track == null) {
            stopEverything()
            return
        }
        loadArt(track)
        session.setMetadata(metadata(track))
        session.setPlaybackState(playbackState())

        val notification = buildNotification()
        if (player.isPlaying) {
            goForeground(notification)
        } else {
            // Paused: let the system reclaim us if it needs to, but leave the controls up.
            if (inForeground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                inForeground = false
            }
            notifications.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            inForeground = true
        } catch (e: RuntimeException) {
            // Android 12+ can refuse this from the background; keep playing and just show controls.
            notifications.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground = false
        notifications.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun loadArt(track: Track) {
        if (track.uri == artTrack) return
        artTrack = track.uri
        art = null
        artJob?.cancel()
        artJob = scope.launch {
            val loaded = AlbumArt.load(this@PlaybackService, track, AlbumArt.LARGE_PX)?.asAndroidBitmap()
            if (loaded != null && player.current?.uri == track.uri) {
                art = loaded
                update()
            }
        }
    }

    private fun metadata(track: Track): MediaMetadata = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
        .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
        .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
        .build()

    private fun playbackState(): PlaybackState = PlaybackState.Builder()
        .setActions(
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or PlaybackState.ACTION_STOP
        )
        .setState(
            if (player.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
            player.livePositionMs,
            if (player.isPlaying) 1f else 0f,
        )
        .build()

    private fun buildNotification(): Notification {
        val track = player.current
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val playing = player.isPlaying
        return builder
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(track?.artist)
            .setSubText(track?.album)
            .setLargeIcon(art)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(playing)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(action(R.drawable.ic_skip_previous, R.string.previous, ACTION_PREVIOUS))
            .addAction(
                if (playing) action(R.drawable.ic_pause, R.string.pause, ACTION_TOGGLE)
                else action(R.drawable.ic_play, R.string.play, ACTION_TOGGLE)
            )
            .addAction(action(R.drawable.ic_skip_next, R.string.next, ACTION_NEXT))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun action(icon: Int, title: Int, action: String) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), getString(title), serviceIntent(action)).build()

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = getString(R.string.playback_channel_description)
                setShowBadge(false)
            }
        notifications.createNotificationChannel(channel)
    }

    /** Remote and system media controls, including the TV remote when the app is in the background. */
    private inner class SessionCallback : MediaSession.Callback() {
        override fun onPlay() = player.resume()
        override fun onPause() = player.pause()
        override fun onStop() = player.pause()
        override fun onSkipToNext() = player.next()
        override fun onSkipToPrevious() = player.prev()
        override fun onSeekTo(pos: Long) = player.seekTo(pos)
        override fun onFastForward() = player.seekBy(10_000)
        override fun onRewind() = player.seekBy(-10_000)
    }
}
