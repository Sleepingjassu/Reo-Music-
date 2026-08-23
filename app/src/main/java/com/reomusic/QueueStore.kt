package com.reomusic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local-only queue snapshot for the single listener on this device. */
object QueueStore {
    private const val PREFS = "reo_queue_state"
    private const val KEY_ITEMS = "items"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"

    data class Snapshot(
        val tracks: List<MusicTrack>,
        val currentIndex: Int,
        val positionMs: Long
    )

    @Synchronized
    fun save(context: Context, tracks: List<MusicTrack>, currentIndex: Int, positionMs: Long) {
        val array = JSONArray()
        tracks.forEach { array.put(TrackJson.toJson(it)) }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .putInt(KEY_INDEX, currentIndex.coerceAtLeast(0))
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    @Synchronized
    fun load(context: Context): Snapshot? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, null) ?: return null
        return try {
            val array = JSONArray(raw)
            val tracks = buildList {
                for (i in 0 until array.length()) {
                    val track = TrackJson.fromJson(array.optJSONObject(i))
                    if (track != null && track.videoId.isNotBlank()) add(track)
                }
            }
            if (tracks.isEmpty()) null else Snapshot(
                tracks = tracks,
                currentIndex = prefs.getInt(KEY_INDEX, 0).coerceIn(0, tracks.lastIndex),
                positionMs = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
