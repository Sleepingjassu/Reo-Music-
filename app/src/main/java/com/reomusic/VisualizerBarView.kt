package com.reomusic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight real-time waveform-derived bar visualizer. Attaches to the
 * given audio session (our own player's session, so no special
 * permission handling is needed beyond RECORD_AUDIO on the manifest).
 */
class VisualizerBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var visualizer: Visualizer? = null

    private val barCount = 32
    private var levels = FloatArray(barCount)
    private var displayLevels = FloatArray(barCount)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C9A6DE")
        style = Paint.Style.FILL
    }

    fun start(audioSessionId: Int) {
        stop()

        if (audioSessionId == 0) return

        try {
            val viz = Visualizer(audioSessionId)
            val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            viz.captureSize = captureSize

            viz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    waveform ?: return
                    updateLevels(waveform)
                }

                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    // Unused: waveform capture is enough for a simple bar display.
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, false)

            viz.enabled = true
            visualizer = viz
        } catch (e: Exception) {
            e.printStackTrace()
            visualizer = null
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        visualizer = null
        levels = FloatArray(barCount)
        displayLevels = FloatArray(barCount)
        postInvalidate()
    }

    private fun updateLevels(waveform: ByteArray) {
        val chunkSize = max(1, waveform.size / barCount)

        for (bar in 0 until barCount) {
            val start = bar * chunkSize
            val end = (start + chunkSize).coerceAtMost(waveform.size)
            if (start >= end) continue

            var sum = 0f
            for (i in start until end) {
                // Unsigned 8-bit PCM centered at 128
                sum += abs(waveform[i].toInt() - 128)
            }
            val avg = sum / (end - start)
            levels[bar] = (avg / 128f).coerceIn(0f, 1f)
        }

        post {
            // Simple smoothing so bars don't jitter frame to frame.
            for (i in levels.indices) {
                displayLevels[i] = displayLevels[i] * 0.55f + levels[i] * 0.45f
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val gap = 4f
        val barWidth = (width - gap * (barCount - 1)) / barCount

        for (i in 0 until barCount) {
            val level = displayLevels.getOrElse(i) { 0f }
            val barHeight = max(4f, level * height)
            val left = i * (barWidth + gap)
            val top = height - barHeight
            canvas.drawRoundRect(left, top, left + barWidth, height, 3f, 3f, paint)
        }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
