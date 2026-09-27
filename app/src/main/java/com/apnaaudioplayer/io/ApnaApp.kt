package com.apnaaudioplayer.io

import android.app.Application

/** Holds the one [Player] for the whole app, so music keeps going after the screen closes. */
class ApnaApp : Application() {

    val player: Player by lazy {
        Player(this).also { player ->
            // The service takes over as soon as something plays; it winds itself down after.
            player.addListener { if (player.isPlaying) PlaybackService.start(this) }
        }
    }
}
