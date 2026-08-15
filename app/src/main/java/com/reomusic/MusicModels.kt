package com.reomusic

data class MusicTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0L
)
