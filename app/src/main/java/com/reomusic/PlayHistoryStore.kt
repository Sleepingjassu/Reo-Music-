package com.reomusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a small local history of played tracks (most recent first) so the
 * Home screen can show "Recently played" and derive "Quick picks" from
 * whatever was played last, without needing an account or a server.
 */
object PlayHistoryStore {

    private const val PREFS_NAME = "reo_music_history"
    private const val KEY_HISTORY = "history_json"
    private const val MAX_ITEMS = 25

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun recordPlay(track: MusicTrack) {

        val store = prefs ?: return

        val current = getRecent().toMutableList()

        // De-duplicate: move to front if already present
        current.removeAll { it.videoId == track.videoId }
        current.add(0, track)

        val trimmed = current.take(MAX_ITEMS)

        val array = JSONArray()
        trimmed.forEach { array.put(toJson(it)) }

        store.edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    @Synchronized
    fun getRecent(): List<MusicTrack> {

        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                fromJson(array.optJSONObject(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun clearAll() {
        prefs?.edit()?.remove(KEY_HISTORY)?.apply()
    }

    private fun toJson(track: MusicTrack): JSONObject {
        return JSONObject().apply {
            put("videoId", track.videoId)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("thumbnailUrl", track.thumbnailUrl)
            put("durationSeconds", track.durationSeconds)
        }
    }

    private fun fromJson(obj: JSONObject?): MusicTrack? {
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
