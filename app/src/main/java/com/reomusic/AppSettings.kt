package com.reomusic

import android.content.Context
import android.content.SharedPreferences

/**
 * Small process-wide settings store backed by SharedPreferences.
 *
 * Kept intentionally simple: [init] should be called once from any
 * entry point (Activity or Service) before the flags are read, and it
 * is safe to call multiple times.
 */
object AppSettings {

    private const val PREFS_NAME = "reo_music_settings"
    private const val KEY_DATA_SAVER = "data_saver_enabled"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_enabled"

    private var prefs: SharedPreferences? = null

    @Volatile
    var dataSaverEnabled: Boolean = false
        private set

    @Volatile
    var keepScreenOnEnabled: Boolean = true
        private set

    @Synchronized
    fun init(context: Context) {

        if (prefs != null) {
            return
        }

        val store =
            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        prefs = store

        dataSaverEnabled =
            store.getBoolean(KEY_DATA_SAVER, false)

        keepScreenOnEnabled =
            store.getBoolean(KEY_KEEP_SCREEN_ON, true)
    }

    fun setDataSaverEnabled(enabled: Boolean) {

        dataSaverEnabled = enabled

        prefs?.edit()
            ?.putBoolean(KEY_DATA_SAVER, enabled)
            ?.apply()
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {

        keepScreenOnEnabled = enabled

        prefs?.edit()
            ?.putBoolean(KEY_KEEP_SCREEN_ON, enabled)
            ?.apply()
    }
}
