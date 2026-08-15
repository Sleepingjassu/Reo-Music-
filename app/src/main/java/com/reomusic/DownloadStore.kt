package com.reomusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class DownloadedTrack(
    val track: MusicTrack,
    val streamUrl: String,
    val downloadedAt: Long
)

/**
 * Records which tracks have been fully cached to disk (see
 * [OfflineCacheManager]) along with the exact stream URL that was used
 * to download them. Reusing that same URL string later is what lets
 * Media3's CacheDataSource serve the whole file straight from disk
 * without any network call, even after the original signed URL expires.
 */
object DownloadStore {

    private const val PREFS_NAME = "reo_music_downloads"
    private const val KEY_DOWNLOADS = "downloads_json"

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
    fun isDownloaded(videoId: String): Boolean = getAll().any { it.track.videoId == videoId }

    @Synchronized
    fun get(videoId: String): DownloadedTrack? = getAll().find { it.track.videoId == videoId }

    @Synchronized
    fun record(track: MusicTrack, streamUrl: String) {
        val current = getAll().filterNot { it.track.videoId == track.videoId }.toMutableList()
        current.add(0, DownloadedTrack(track, streamUrl, System.currentTimeMillis()))
        save(current)
        notifyChanged()
    }

    @Synchronized
    fun remove(videoId: String) {
        val current = getAll().filterNot { it.track.videoId == videoId }
        save(current)
        notifyChanged()
    }

    @Synchronized
    fun getAll(): List<DownloadedTrack> {
        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> fromJson(array.optJSONObject(i)) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun save(items: List<DownloadedTrack>) {
        val store = prefs ?: return
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        store.edit().putString(KEY_DOWNLOADS, array.toString()).apply()
    }

    private fun toJson(item: DownloadedTrack): JSONObject {
        return JSONObject().apply {
            put("track", TrackJson.toJson(item.track))
            put("streamUrl", item.streamUrl)
            put("downloadedAt", item.downloadedAt)
        }
    }

    private fun fromJson(obj: JSONObject?): DownloadedTrack? {
        obj ?: return null
        val track = TrackJson.fromJson(obj.optJSONObject("track")) ?: return null
        val streamUrl = obj.optString("streamUrl")
        if (streamUrl.isBlank()) return null
        return DownloadedTrack(track, streamUrl, obj.optLong("downloadedAt", 0L))
    }
}
