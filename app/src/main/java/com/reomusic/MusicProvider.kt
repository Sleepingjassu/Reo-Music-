package com.reomusic

/**
 * Result of resolving a single track: its playable audio URL plus a list
 * of related tracks pulled from the same network call, used to build an
 * algorithmic "up next" queue (similar to YouTube Music radio/autoplay).
 */
data class StreamResolution(
    val streamUrl: String,
    val relatedTracks: List<MusicTrack> = emptyList()
)

interface MusicProvider {

    suspend fun search(query: String): List<MusicTrack>

    /**
     * Resolves only the playable URL for [videoId]. Cheap building block
     * used when queuing up already-known related tracks.
     */
    suspend fun getStreamUrl(videoId: String): String?

    /**
     * Resolves the playable URL for [videoId] AND returns related tracks
     * from the same round trip, so the app can start playback immediately
     * and build a smart "up next" queue without extra requests.
     */
    suspend fun resolveTrack(videoId: String): StreamResolution?
}
