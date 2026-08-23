package com.reomusic

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Small process-wide settings store backed by SharedPreferences.
 *
 * [init] should be called once from any entry point (Activity or
 * Service) before the flags are read, and is safe to call repeatedly.
 */
object AppSettings {

    private const val PREFS_NAME = "reo_music_settings"
    private const val KEY_DATA_SAVER = "data_saver_enabled"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_enabled"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_SMART_SHUFFLE = "smart_shuffle_enabled"
    private const val KEY_SKIP_SILENCE = "skip_silence_enabled"
    private const val KEY_CROSSFADE = "crossfade_enabled"
    private const val KEY_CROSSFADE_DURATION_MS = "crossfade_duration_ms"
    private const val KEY_PLAYBACK_SPEED = "playback_speed"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_BANDS = "eq_bands" // comma separated millibels

    private var prefs: SharedPreferences? = null

    @Volatile var dataSaverEnabled: Boolean = false; private set
    @Volatile var keepScreenOnEnabled: Boolean = true; private set
    @Volatile var themeMode: ThemeMode = ThemeMode.SYSTEM; private set
    @Volatile var smartShuffleEnabled: Boolean = false; private set
    @Volatile var skipSilenceEnabled: Boolean = false; private set
    @Volatile var crossfadeEnabled: Boolean = false; private set
    @Volatile var crossfadeDurationMs: Int = 4000; private set
    @Volatile var playbackSpeed: Float = 1.0f; private set
    @Volatile var repeatMode: Int = 0; private set // matches androidx.media3.common.Player.REPEAT_MODE_*
    @Volatile var wifiOnlyDownloads: Boolean = false; private set
    @Volatile var equalizerEnabled: Boolean = false; private set
    @Volatile var equalizerPreset: String = "Normal"; private set
    @Volatile var equalizerBands: IntArray = IntArray(5); private set

    @Synchronized
    fun init(context: Context) {

        if (prefs != null) return

        val store = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs = store

        dataSaverEnabled = store.getBoolean(KEY_DATA_SAVER, false)
        keepScreenOnEnabled = store.getBoolean(KEY_KEEP_SCREEN_ON, true)
        smartShuffleEnabled = store.getBoolean(KEY_SMART_SHUFFLE, false)
        skipSilenceEnabled = store.getBoolean(KEY_SKIP_SILENCE, false)
        crossfadeEnabled = store.getBoolean(KEY_CROSSFADE, false)
        crossfadeDurationMs = store.getInt(KEY_CROSSFADE_DURATION_MS, 4000)
        playbackSpeed = store.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
        repeatMode = store.getInt(KEY_REPEAT_MODE, 0)
        wifiOnlyDownloads = store.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, false)
        equalizerEnabled = store.getBoolean(KEY_EQ_ENABLED, false)
        equalizerPreset = store.getString(KEY_EQ_PRESET, "Normal") ?: "Normal"

        themeMode = try {
            ThemeMode.valueOf(store.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        val bandsRaw = store.getString(KEY_EQ_BANDS, null)
        equalizerBands = if (bandsRaw.isNullOrBlank()) {
            IntArray(5)
        } else {
            try {
                bandsRaw.split(",").map { it.trim().toInt() }.toIntArray()
            } catch (e: Exception) {
                IntArray(5)
            }
        }
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        dataSaverEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_DATA_SAVER, enabled)?.apply()
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        keepScreenOnEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_KEEP_SCREEN_ON, enabled)?.apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        skipSilenceEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_SKIP_SILENCE, enabled)?.apply()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        crossfadeEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_CROSSFADE, enabled)?.apply()
    }

    fun setCrossfadeDurationMs(ms: Int) {
        crossfadeDurationMs = ms
        prefs?.edit()?.putInt(KEY_CROSSFADE_DURATION_MS, ms)?.apply()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        prefs?.edit()?.putFloat(KEY_PLAYBACK_SPEED, speed)?.apply()
    }

    fun setRepeatMode(mode: Int) {
        repeatMode = mode
        prefs?.edit()?.putInt(KEY_REPEAT_MODE, mode)?.apply()
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        wifiOnlyDownloads = enabled
        prefs?.edit()?.putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled)?.apply()
    }

    fun setSmartShuffleEnabled(enabled: Boolean) {
        smartShuffleEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_SMART_SHUFFLE, enabled)?.apply()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_EQ_ENABLED, enabled)?.apply()
    }

    fun setEqualizerPreset(preset: String) {
        equalizerPreset = preset
        prefs?.edit()?.putString(KEY_EQ_PRESET, preset)?.apply()
    }

    fun setEqualizerBands(bands: IntArray) {
        equalizerBands = bands
        prefs?.edit()?.putString(KEY_EQ_BANDS, bands.joinToString(","))?.apply()
    }
}
