package com.reomusic

import android.content.Context
import android.content.SharedPreferences

/** Local-only preferences for the single listener. No accounts or network state. */
object LocalFeatureStore {
    private const val PREFS = "reo_local_features"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_ANIMATIONS = "animations"
    private const val KEY_REDUCED_MOTION = "reduced_motion"
    private const val KEY_AUTO_CLEANUP = "auto_cleanup"
    private const val KEY_WIFI_STREAMING_ONLY = "wifi_streaming_only"
    private const val KEY_HIGH_QUALITY_ART = "high_quality_art"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_SHOW_LYRICS = "show_lyrics"
    private const val KEY_PERSIST_QUEUE = "persist_queue"
    private const val KEY_STARTUP_RESUME = "startup_resume"

    private var prefs: SharedPreferences? = null
    @Volatile var dynamicColor = true; private set
    @Volatile var amoled = false; private set
    @Volatile var animations = true; private set
    @Volatile var reducedMotion = false; private set
    @Volatile var autoCleanup = true; private set
    @Volatile var wifiStreamingOnly = false; private set
    @Volatile var highQualityArtwork = true; private set
    @Volatile var haptics = true; private set
    @Volatile var showLyrics = true; private set
    @Volatile var persistQueue = true; private set
    @Volatile var startupResume = true; private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val p = prefs ?: return
        dynamicColor = p.getBoolean(KEY_DYNAMIC_COLOR, true)
        amoled = p.getBoolean(KEY_AMOLED, false)
        animations = p.getBoolean(KEY_ANIMATIONS, true)
        reducedMotion = p.getBoolean(KEY_REDUCED_MOTION, false)
        autoCleanup = p.getBoolean(KEY_AUTO_CLEANUP, true)
        wifiStreamingOnly = p.getBoolean(KEY_WIFI_STREAMING_ONLY, false)
        highQualityArtwork = p.getBoolean(KEY_HIGH_QUALITY_ART, true)
        haptics = p.getBoolean(KEY_HAPTICS, true)
        showLyrics = p.getBoolean(KEY_SHOW_LYRICS, true)
        persistQueue = p.getBoolean(KEY_PERSIST_QUEUE, true)
        startupResume = p.getBoolean(KEY_STARTUP_RESUME, true)
    }

    private fun set(key: String, value: Boolean) { prefs?.edit()?.putBoolean(key, value)?.apply() }
    fun setDynamicColor(v: Boolean) { dynamicColor = v; set(KEY_DYNAMIC_COLOR, v) }
    fun setAmoled(v: Boolean) { amoled = v; set(KEY_AMOLED, v) }
    fun setAnimations(v: Boolean) { animations = v; set(KEY_ANIMATIONS, v) }
    fun setReducedMotion(v: Boolean) { reducedMotion = v; set(KEY_REDUCED_MOTION, v) }
    fun setAutoCleanup(v: Boolean) { autoCleanup = v; set(KEY_AUTO_CLEANUP, v) }
    fun setWifiStreamingOnly(v: Boolean) { wifiStreamingOnly = v; set(KEY_WIFI_STREAMING_ONLY, v) }
    fun setHighQualityArtwork(v: Boolean) { highQualityArtwork = v; set(KEY_HIGH_QUALITY_ART, v) }
    fun setHaptics(v: Boolean) { haptics = v; set(KEY_HAPTICS, v) }
    fun setShowLyrics(v: Boolean) { showLyrics = v; set(KEY_SHOW_LYRICS, v) }
    fun setPersistQueue(v: Boolean) { persistQueue = v; set(KEY_PERSIST_QUEUE, v) }
    fun setStartupResume(v: Boolean) { startupResume = v; set(KEY_STARTUP_RESUME, v) }
}
