package com.reomusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Liked Songs" collection. Most-recently-liked first.
 */
object FavoritesStore {

    private const val PREFS_NAME = "reo_music_favorites"
    private const val KEY_TRACKS = "favorites_json"

    private var prefs: SharedPreferences? = null
    private val listeners = mutableSetOf<() -> Unit>()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }

    @Synchronized
    fun isLiked(videoId: String): Boolean {
        return getAll().any { it.videoId == videoId }
    }

    @Synchronized
    fun toggle(track: MusicTrack): Boolean {
        val current = getAll().toMutableList()
        val existingIndex = current.indexOfFirst { it.videoId == track.videoId }

        val nowLiked: Boolean
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            nowLiked = false
        } else {
            current.add(0, track)
            nowLiked = true
        }

        save(current)
        notifyChanged()
        return nowLiked
    }

    fun remove(videoId: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.videoId == videoId }
        save(current)
        notifyChanged()
    }

    @Synchronized
    fun getAll(): List<MusicTrack> {
        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY_TRACKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> TrackJson.fromJson(array.optJSONObject(i)) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun save(tracks: List<MusicTrack>) {
        val store = prefs ?: return
        val array = JSONArray()
        tracks.forEach { array.put(TrackJson.toJson(it)) }
        store.edit().putString(KEY_TRACKS, array.toString()).apply()
    }
}

/** Shared MusicTrack <-> JSON helpers used by the various local stores. */
object TrackJson {

    fun toJson(track: MusicTrack): JSONObject {
        return JSONObject().apply {
            put("videoId", track.videoId)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("thumbnailUrl", track.thumbnailUrl)
            put("durationSeconds", track.durationSeconds)
        }
    }

    fun fromJson(obj: JSONObject?): MusicTrack? {
        obj ?: return null
        val videoId = obj.optString("videoId")
        if (videoId.isBlank()) return null

        return MusicTrack(
            videoId = videoId,
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = obj.optString("album", ""),
            thumbnailUrl = obj.optString("thumbnailUrl", ""),
            durationSeconds = obj.optLong("durationSeconds", 0L)
        )
    }
}
