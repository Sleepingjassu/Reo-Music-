package com.reomusic

interface MusicProvider {

    suspend fun search(query: String): List<MusicTrack>

    suspend fun getStreamUrl(videoId: String): String?
}
