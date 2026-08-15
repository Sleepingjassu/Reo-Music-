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
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_BANDS = "eq_bands" // comma separated millibels

    private var prefs: SharedPreferences? = null

    @Volatile var dataSaverEnabled: Boolean = false; private set
    @Volatile var keepScreenOnEnabled: Boolean = true; private set
    @Volatile var themeMode: ThemeMode = ThemeMode.SYSTEM; private set
    @Volatile var smartShuffleEnabled: Boolean = false; private set
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
