package com.reomusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val tracks: List<MusicTrack>
)

object PlaylistStore {

    private const val PREFS_NAME = "reo_music_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"

    private var prefs: SharedPreferences? = null
    private val listeners = mutableSetOf<() -> Unit>()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { listeners.forEach { it.invoke() } }

    @Synchronized
    fun getAll(): List<Playlist> {
        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> fromJson(array.optJSONObject(i)) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun get(id: String): Playlist? = getAll().find { it.id == id }

    @Synchronized
    fun create(name: String): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "New Playlist" },
            createdAt = System.currentTimeMillis(),
            tracks = emptyList()
        )
        val all = getAll().toMutableList()
        all.add(0, playlist)
        save(all)
        notifyChanged()
        return playlist
    }

    fun rename(id: String, newName: String) {
        val all = getAll().map {
            if (it.id == id) it.copy(name = newName.ifBlank { it.name }) else it
        }
        save(all)
        notifyChanged()
    }

    fun delete(id: String) {
        val all = getAll().filterNot { it.id == id }
        save(all)
        notifyChanged()
    }

    fun addTrack(id: String, track: MusicTrack) {
        val all = getAll().map { playlist ->
            if (playlist.id == id && playlist.tracks.none { it.videoId == track.videoId }) {
                playlist.copy(tracks = playlist.tracks + track)
            } else playlist
        }
        save(all)
        notifyChanged()
    }

    fun removeTrack(id: String, videoId: String) {
        val all = getAll().map { playlist ->
            if (playlist.id == id) {
                playlist.copy(tracks = playlist.tracks.filterNot { it.videoId == videoId })
            } else playlist
        }
        save(all)
        notifyChanged()
    }

    fun reorder(id: String, fromIndex: Int, toIndex: Int) {
        val all = getAll().map { playlist ->
            if (playlist.id == id) {
                val mutable = playlist.tracks.toMutableList()
                if (fromIndex in mutable.indices && toIndex in mutable.indices) {
                    val item = mutable.removeAt(fromIndex)
                    mutable.add(toIndex, item)
                }
                playlist.copy(tracks = mutable)
            } else playlist
        }
        save(all)
        notifyChanged()
    }

    private fun save(playlists: List<Playlist>) {
        val store = prefs ?: return
        val array = JSONArray()
        playlists.forEach { array.put(toJson(it)) }
        store.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    private fun toJson(playlist: Playlist): JSONObject {
        val tracksArray = JSONArray()
        playlist.tracks.forEach { tracksArray.put(TrackJson.toJson(it)) }
        return JSONObject().apply {
            put("id", playlist.id)
            put("name", playlist.name)
            put("createdAt", playlist.createdAt)
            put("tracks", tracksArray)
        }
    }

    private fun fromJson(obj: JSONObject?): Playlist? {
        obj ?: return null
        val id = obj.optString("id")
        if (id.isBlank()) return null

        val tracksArray = obj.optJSONArray("tracks") ?: JSONArray()
        val tracks = (0 until tracksArray.length()).mapNotNull {
            TrackJson.fromJson(tracksArray.optJSONObject(it))
        }

        return Playlist(
            id = id,
            name = obj.optString("name", "Playlist"),
            createdAt = obj.optLong("createdAt", 0L),
            tracks = tracks
        )
    }
}
