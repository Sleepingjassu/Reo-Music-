package com.reomusic

import android.media.audiofx.Equalizer

/**
 * Thin wrapper around [Equalizer], the platform audio-effect that can be
 * attached to any audio session created by this app (ExoPlayer's included)
 * without needing a special permission, since the session belongs to us.
 *
 * Re-created whenever the player's audio session id changes (e.g. after
 * the ExoPlayer instance is rebuilt), see [attach].
 */
object EqualizerManager {

    val presets = listOf("Normal", "Bass Boost", "Treble Boost", "Vocal", "Custom")

    private var equalizer: Equalizer? = null

    val numberOfBands: Int
        get() = equalizer?.numberOfBands?.toInt() ?: 5

    fun attach(audioSessionId: Int) {
        release()

        if (audioSessionId == 0) return

        try {
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = AppSettings.equalizerEnabled
            equalizer = eq

            val savedBands = AppSettings.equalizerBands
            if (savedBands.size == eq.numberOfBands.toInt() && AppSettings.equalizerPreset == "Custom") {
                applyBandLevels(savedBands)
            } else {
                applyPreset(AppSettings.equalizerPreset)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            equalizer = null
        }
    }

    fun setEnabled(enabled: Boolean) {
        AppSettings.setEqualizerEnabled(enabled)
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun bandLevelRange(): IntArray {
        return try {
            equalizer?.bandLevelRange?.let { range -> intArrayOf(range[0].toInt(), range[1].toInt()) }
                ?: intArrayOf(-1500, 1500)
        } catch (e: Exception) {
            intArrayOf(-1500, 1500)
        }
    }

    fun getBandLevel(band: Int): Int {
        return try {
            equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getCenterFreqHz(band: Int): Int {
        return try {
            (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000
        } catch (e: Exception) {
            0
        }
    }

    fun setBandLevel(band: Int, level: Int) {
        try {
            equalizer?.setBandLevel(band.toShort(), level.toShort())
            AppSettings.setEqualizerPreset("Custom")

            val bands = IntArray(numberOfBands) { i -> getBandLevel(i) }
            AppSettings.setEqualizerBands(bands)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyPreset(name: String) {
        val eq = equalizer ?: run {
            AppSettings.setEqualizerPreset(name)
            return
        }

        try {
            when (name) {
                "Normal" -> flatten(eq)
                "Bass Boost" -> shape(eq, intArrayOf(900, 500, 100, -200, -400))
                "Treble Boost" -> shape(eq, intArrayOf(-400, -200, 100, 500, 900))
                "Vocal" -> shape(eq, intArrayOf(-200, 200, 700, 300, -100))
                else -> { /* Custom: leave levels as the user set them */ }
            }
            AppSettings.setEqualizerPreset(name)
            if (name != "Custom") {
                AppSettings.setEqualizerBands(IntArray(numberOfBands) { i -> getBandLevel(i) })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun flatten(eq: Equalizer) {
        for (i in 0 until eq.numberOfBands.toInt()) {
            eq.setBandLevel(i.toShort(), 0)
        }
    }

    private fun shape(eq: Equalizer, millibelsPerBand: IntArray) {
        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        for (i in 0 until bandCount) {
            val target = millibelsPerBand.getOrElse(i) { 0 }
            val clamped = target.coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(i.toShort(), clamped.toShort())
        }
    }

    fun applyBandLevels(levels: IntArray) {
        val eq = equalizer ?: return
        try {
            for (i in levels.indices) {
                if (i < eq.numberOfBands) {
                    eq.setBandLevel(i.toShort(), levels[i].toShort())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
    }
}
