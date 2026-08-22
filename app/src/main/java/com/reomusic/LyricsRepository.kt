package com.reomusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class SyncedLyricLine(
    val startMs: Long,
    val text: String,
    val endMs: Long = Long.MAX_VALUE
)

data class LyricsResult(
    val plainText: String,
    val lines: List<SyncedLyricLine>
)

object LyricsRepository {
    private val client = OkHttpClient.Builder().build()

    suspend fun getLyrics(title: String, artist: String): LyricsResult? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            val url = "https://lrclib.net/api/search?track_name=${java.net.URLEncoder.encode(title, "UTF-8")}&artist_name=${java.net.URLEncoder.encode(artist, "UTF-8")}"
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", "REO-Music/1.0").build()).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            val array = JSONArray(body)
            if (array.length() == 0) return@withContext null

            var bestSynced = ""
            var bestPlain = ""
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val synced = item.optString("syncedLyrics")
                val plain = item.optString("plainLyrics")
                if (synced.isNotBlank()) { bestSynced = synced; bestPlain = plain; break }
                if (bestPlain.isBlank()) bestPlain = plain
            }
            if (bestSynced.isBlank() && bestPlain.isBlank()) return@withContext null

            val parsed = parseLrc(bestSynced)
            LyricsResult(if (bestPlain.isNotBlank()) bestPlain else parsed.joinToString("\n") { it.text }, parsed)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLrc(raw: String): List<SyncedLyricLine> {
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]\\s*(.*)")
        val entries = raw.lineSequence().mapNotNull { line ->
            val m = regex.matchEntire(line.trim()) ?: return@mapNotNull null
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            SyncedLyricLine(minutes * 60_000 + seconds * 1000 + fraction, m.groupValues[4].trim())
        }.filter { it.text.isNotBlank() }.sortedBy { it.startMs }.toMutableList()
        for (i in 0 until entries.lastIndex) entries[i] = entries[i].copy(endMs = entries[i + 1].startMs)
        if (entries.isNotEmpty()) entries[entries.lastIndex] = entries.last().copy(endMs = Long.MAX_VALUE)
        return entries
    }
}
