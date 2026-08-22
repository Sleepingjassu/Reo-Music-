package com.reomusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln

/**
 * A small on-device implicit-feedback model.
 *
 * This is NOT collaborative filtering — there's only one user, and
 * collaborative filtering needs a population to find patterns across.
 * What this *is*: a session co-occurrence + completion-weighted ranking
 * model, built entirely from this device's own listening behavior:
 *
 *  - Completion vs. skip: a track you let finish is a stronger positive
 *    signal than one you skip after 10 seconds.
 *  - Co-occurrence: tracks you tend to play back-to-back get linked, so
 *    "played after X" becomes a real signal, not just "related by genre".
 *  - Recency + frequency: classic exponential recency decay + log-scaled
 *    play count, same idea Spotify's own simpler heuristics use.
 *
 * All of this stays on-device. No server, no accounts, no other users.
 */
object PlaybackSignalStore {

    private const val PREFS_NAME = "reo_music_signals"
    private const val KEY_TRACKS = "signal_tracks_json"
    private const val KEY_COOCCURRENCE = "signal_cooccurrence_json"

    private const val COMPLETION_THRESHOLD = 0.85 // played >=85% counts as "completed"
    private const val RECENCY_HALF_LIFE_DAYS = 10.0

    private data class TrackStats(
        val track: MusicTrack,
        var playCount: Int = 0,
        var completeCount: Int = 0,
        var skipCount: Int = 0,
        var lastPlayedAt: Long = 0L
    )

    private var prefs: SharedPreferences? = null
    private val trackStats = mutableMapOf<String, TrackStats>()
    private val cooccurrence = mutableMapOf<String, MutableMap<String, Int>>()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    /**
     * Call whenever playback moves from one item to the next (including
     * "first ever track", where [previous] is null). Captures the
     * completion/skip outcome for [previous] and links it to [current]
     * for co-occurrence.
     */
    @Synchronized
    fun recordTransition(
        previous: MusicTrack?,
        previousPositionMs: Long,
        previousDurationMs: Long,
        current: MusicTrack
    ) {
        if (previous != null && previousDurationMs > 0) {
            val ratio = previousPositionMs.toDouble() / previousDurationMs.toDouble()
            val stats = trackStats.getOrPut(previous.videoId) { TrackStats(previous) }
            if (ratio >= COMPLETION_THRESHOLD) {
                stats.completeCount++
            } else {
                stats.skipCount++
            }

            // Session co-occurrence: previous -> current
            val bucket = cooccurrence.getOrPut(previous.videoId) { mutableMapOf() }
            bucket[current.videoId] = (bucket[current.videoId] ?: 0) + 1
        }

        val currentStats = trackStats.getOrPut(current.videoId) { TrackStats(current) }
        currentStats.playCount++
        currentStats.lastPlayedAt = System.currentTimeMillis()

        save()
    }

    /** Artists ranked by a blend of frequency, completion rate, and recency. */
    fun topArtists(limit: Int = 3): List<String> {
        val byArtist = mutableMapOf<String, Double>()
        for (stats in trackStats.values) {
            if (stats.track.artist.isBlank()) continue
            byArtist[stats.track.artist] = (byArtist[stats.track.artist] ?: 0.0) + affinityScore(stats)
        }
        return byArtist.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** Ranks [candidates] using co-occurrence with [seedVideoId] plus general affinity. */
    fun rankByAffinity(seedVideoId: String?, candidates: List<MusicTrack>): List<MusicTrack> {
        val cooccurBucket = seedVideoId?.let { cooccurrence[it] } ?: emptyMap()

        return candidates.sortedByDescending { track ->
            val stats = trackStats[track.videoId]
            val base = stats?.let { affinityScore(it) } ?: 0.0
            val coBoost = (cooccurBucket[track.videoId] ?: 0) * 5.0
            base + coBoost
        }
    }

    private fun affinityScore(stats: TrackStats): Double {
        val totalOutcomes = stats.completeCount + stats.skipCount
        val completionRate = if (totalOutcomes > 0) stats.completeCount.toDouble() / totalOutcomes else 0.5

        val daysSincePlayed = (System.currentTimeMillis() - stats.lastPlayedAt) / 86_400_000.0
        val recencyWeight = exp(-ln(2.0) * daysSincePlayed / RECENCY_HALF_LIFE_DAYS)

        val frequencyWeight = ln(1.0 + stats.playCount)

        return frequencyWeight * 2.0 + completionRate * 1.5 + recencyWeight * 1.0
    }

    private fun save() {
        val store = prefs ?: return

        val tracksJson = JSONObject()
        trackStats.forEach { (videoId, stats) ->
            tracksJson.put(videoId, JSONObject().apply {
                put("track", TrackJson.toJson(stats.track))
                put("playCount", stats.playCount)
                put("completeCount", stats.completeCount)
                put("skipCount", stats.skipCount)
                put("lastPlayedAt", stats.lastPlayedAt)
            })
        }

        val coJson = JSONObject()
        cooccurrence.forEach { (videoId, bucket) ->
            val bucketJson = JSONObject()
            bucket.forEach { (otherId, count) -> bucketJson.put(otherId, count) }
            coJson.put(videoId, bucketJson)
        }

        store.edit()
            .putString(KEY_TRACKS, tracksJson.toString())
            .putString(KEY_COOCCURRENCE, coJson.toString())
            .apply()
    }

    private fun load() {
        val store = prefs ?: return

        try {
            store.getString(KEY_TRACKS, null)?.let { raw ->
                val obj = JSONObject(raw)
                obj.keys().forEach { videoId ->
                    val entry = obj.getJSONObject(videoId)
                    val track = TrackJson.fromJson(entry.optJSONObject("track")) ?: return@forEach
                    trackStats[videoId] = TrackStats(
                        track = track,
                        playCount = entry.optInt("playCount", 0),
                        completeCount = entry.optInt("completeCount", 0),
                        skipCount = entry.optInt("skipCount", 0),
                        lastPlayedAt = entry.optLong("lastPlayedAt", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            store.getString(KEY_COOCCURRENCE, null)?.let { raw ->
                val obj = JSONObject(raw)
                obj.keys().forEach { videoId ->
                    val bucketJson = obj.getJSONObject(videoId)
                    val bucket = mutableMapOf<String, Int>()
                    bucketJson.keys().forEach { otherId -> bucket[otherId] = bucketJson.optInt(otherId, 0) }
                    cooccurrence[videoId] = bucket
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
