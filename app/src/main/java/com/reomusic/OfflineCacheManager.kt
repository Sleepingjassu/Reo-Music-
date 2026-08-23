package com.reomusic

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Wraps a single on-disk [SimpleCache] shared by every player instance.
 *
 * Two things use it:
 *  1. Every stream played is progressively cached automatically (via the
 *     [CacheDataSource.Factory] handed to ExoPlayer), so replaying a
 *     recently-played song is instant and uses no data.
 *  2. [downloadFully] explicitly pulls an entire track to disk up front
 *     for guaranteed offline playback later (the "Downloads" feature).
 *
 * Downloaded tracks stay playable offline even after the original signed
 * URL "expires" because CacheDataSource never needs to re-hit that URL
 * once every byte of the requested range is already on disk.
 */
@UnstableApi
object OfflineCacheManager {

    private const val MAX_CACHE_BYTES = 600L * 1024 * 1024 // 600MB

    @Volatile
    private var cache: SimpleCache? = null

    @Synchronized
    fun init(context: Context) {
        if (cache != null) return

        val cacheDir = File(context.applicationContext.cacheDir, "reo_media_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
        val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)

        cache = SimpleCache(cacheDir, evictor, databaseProvider)
    }

    private fun upstreamFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("REO-Music/1.0 (Android)")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
    }

    /**
     * Factory to hand to ExoPlayer's MediaSource.Factory so every stream
     * transparently reads/writes through the shared disk cache.
     */
    fun cacheDataSourceFactory(): CacheDataSource.Factory {
        val simpleCache = cache ?: throw IllegalStateException("OfflineCacheManager.init() not called")

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun isFullyCached(streamUrl: String): Boolean {
        val simpleCache = cache ?: return false
        return try {
            val length = ContentMetadata.getContentLength(simpleCache.getContentMetadata(streamUrl))
            if (length <= 0) return false
            val cachedBytes = simpleCache.getCachedBytes(streamUrl, 0, length)
            cachedBytes >= length
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Downloads [streamUrl] fully into the cache so it's guaranteed
     * playable offline afterwards. Suspends until finished; [onProgress]
     * receives 0f..1f.
     */
    suspend fun downloadFully(
        streamUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = suspendCancellableCoroutine { continuation ->

        val simpleCache = cache
        if (simpleCache == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val dataSpec = DataSpec.Builder()
            .setUri(android.net.Uri.parse(streamUrl))
            .build()

        val upstream = upstreamFactory().createDataSource()

        // Route the write through a CacheDataSource wrapping our shared
        // SimpleCache so the downloaded bytes actually land in it.
        val cachingDataSource = CacheDataSource(
            simpleCache,
            upstream,
            FileDataSource(),
            CacheDataSink(simpleCache, Long.MAX_VALUE),
            CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            null
        )

        val writer = CacheWriter(
            cachingDataSource,
            dataSpec,
            null
        ) { requestLength, bytesCached, _ ->
            if (requestLength > 0) {
                onProgress((bytesCached.toFloat() / requestLength.toFloat()).coerceIn(0f, 1f))
            }
        }

        Thread {
            try {
                writer.cache()
                continuation.resume(true)
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume(false)
            }
        }.start()

        continuation.invokeOnCancellation {
            try {
                writer.cancel()
            } catch (_: Exception) {}
        }
    }

    fun removeFromCache(streamUrl: String) {
        val simpleCache = cache ?: return
        try {
            simpleCache.removeResource(streamUrl)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun currentCacheSizeBytes(): Long {
        return cache?.cacheSpace ?: 0L
    }

    @Synchronized
    fun clearCache() {
        val simpleCache = cache ?: return
        try {
            val keys = simpleCache.keys.toList()
            keys.forEach { key -> simpleCache.removeResource(key) }
        } catch (_: Exception) {
            // Cache cleanup is best-effort and must never take playback down.
        }
    }
}
