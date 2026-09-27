package com.apnaaudioplayer.io

import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String?,
    /** Track number within the album, or 0 when unknown. */
    val trackNumber: Int,
    val durationMs: Long,
    /** Storage volume the file lives on, e.g. `external_primary` or a USB drive's id. */
    val volume: String,
    /** Folder path within [volume], e.g. `Music/Road Trip`; empty for the volume root. */
    val folder: String,
    val mime: String,
)
