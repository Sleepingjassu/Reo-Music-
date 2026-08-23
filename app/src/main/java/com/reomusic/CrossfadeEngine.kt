package com.reomusic

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource

/**
 * True dual-player crossfade.
 *
 * A single ExoPlayer instance can only decode/output one audio stream at
 * a time, so a genuine overlapping crossfade needs two players producing
 * real, simultaneously-mixed audio — not one player's volume ramped down
 * and back up across a silent gap (that's a fade, not a crossfade, and
 * REO used to do exactly that; this replaces it).
 *
 * How it actually works:
 *  1. As [primary] nears the end of the current track, a [shadow]
 *     ExoPlayer is created, given the *next* track, and started at
 *     volume 0 — genuinely playing in parallel with [primary]. Both are
 *     real AudioTracks, mixed by the OS.
 *  2. Over the configured crossfade duration, primary fades 1→0 while
 *     shadow fades 0→1. The sound during this window is coming from two
 *     independent decoders, not one.
 *  3. [primary] is left completely alone to auto-advance through its own
 *     existing playlist as normal, so "up next" / prev / next stay
 *     perfectly accurate with zero extra queue bookkeeping. By the time
 *     it naturally reaches the next item, its volume is already ~0, so
 *     that auto-advance is inaudible.
 *  4. Once primary lands on the same item shadow is already playing, we
 *     seek primary to shadow's current position and swap audibility back
 *     to primary, then release shadow. This handoff is a `seekTo` +
 *     volume swap on an already-buffered stream — not a media item
 *     replacement — so it's fast and low-risk.
 *
 * If the user skips, seeks, or pauses mid-transition, [cancel] tears the
 * shadow player down and restores primary to full volume immediately.
 *
 * Known limitation: crossfading into a wrapped-around REPEAT_MODE_ALL
 * loop point, and REPEAT_MODE_ONE, are both deliberately skipped rather
 * than guessed at — see [onPositionTick].
 */
@UnstableApi
class CrossfadeEngine(
    private val context: Context,
    private val primary: ExoPlayer,
    private val mediaSourceFactory: MediaSource.Factory,
    private val audioAttributes: AudioAttributes
) {
    private enum class State { IDLE, FADING, HANDOFF }

    private var state = State.IDLE
    private var shadow: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var fadeStartAt = 0L
    private var fadeDurationMs = 0L
    private var armedForItemId: String? = null

    private val primaryListener = object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (state == State.FADING && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                performHandoff()
            } else if (state != State.IDLE) {
                // User manually skipped/changed tracks mid-fade — abandon cleanly.
                cancel()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && state != State.IDLE) {
                cancel()
            }
        }
    }

    init {
        primary.addListener(primaryListener)
    }

    /** Call periodically (e.g. every 250ms) from the service's tick loop. */
    fun onPositionTick() {
        if (!AppSettings.crossfadeEnabled) {
            if (state != State.IDLE) cancel()
            return
        }

        if (state != State.IDLE) {
            driveFade()
            return
        }

        if (!primary.isPlaying) return
        if (primary.repeatMode == Player.REPEAT_MODE_ONE) return // would crossfade a track into itself

        val duration = primary.duration
        val position = primary.currentPosition
        if (duration <= 0) return

        val configuredMs = AppSettings.crossfadeDurationMs.toLong()
        if (configuredMs <= 0) return

        val nextIndex = primary.currentMediaItemIndex + 1
        if (nextIndex >= primary.mediaItemCount) return // last item — including REPEAT_ALL wrap, skipped deliberately

        val remaining = duration - position
        // Never fade for longer than half the track, so short tracks don't
        // start crossfading almost immediately after they begin.
        val effectiveDuration = minOf(configuredMs, duration / 2)
        if (remaining > effectiveDuration || remaining <= 0) return

        val nextItem = primary.getMediaItemAt(nextIndex)
        if (armedForItemId == nextItem.mediaId) return // already armed for this transition

        armCrossfade(nextItem, effectiveDuration, remaining)
    }

    private fun armCrossfade(nextItem: MediaItem, effectiveDurationMs: Long, remainingMs: Long) {
        val shadowPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, false) // primary already owns audio focus
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        shadowPlayer.setMediaItem(nextItem)
        shadowPlayer.volume = 0f
        shadowPlayer.prepare()
        shadowPlayer.playWhenReady = true

        shadow = shadowPlayer
        armedForItemId = nextItem.mediaId
        fadeStartAt = System.currentTimeMillis()
        fadeDurationMs = minOf(effectiveDurationMs, remainingMs).coerceAtLeast(300L)
        state = State.FADING
    }

    private fun driveFade() {
        if (state != State.FADING) return
        val shadowPlayer = shadow ?: run { state = State.IDLE; return }

        val elapsed = System.currentTimeMillis() - fadeStartAt
        val t = (elapsed.toFloat() / fadeDurationMs).coerceIn(0f, 1f)

        primary.volume = 1f - t
        shadowPlayer.volume = t
        // Actual handoff waits for onMediaItemTransition(AUTO) on primary
        // (see primaryListener above), tying it to primary's real playlist
        // position rather than a timer guess.
    }

    private fun performHandoff() {
        val shadowPlayer = shadow
        if (shadowPlayer == null) {
            state = State.IDLE
            return
        }

        state = State.HANDOFF

        val shadowPosition = shadowPlayer.currentPosition
        primary.seekTo(shadowPosition)
        primary.volume = 1f
        primary.playWhenReady = true

        shadowPlayer.volume = 0f
        shadowPlayer.playWhenReady = false

        val toRelease = shadowPlayer
        handler.postDelayed({
            toRelease.release()
            if (shadow === toRelease) shadow = null
        }, 250L)

        armedForItemId = null
        state = State.IDLE
    }

    /** Tears down any in-progress crossfade and restores primary to full volume. */
    fun cancel() {
        val toRelease = shadow
        shadow = null
        armedForItemId = null
        state = State.IDLE
        primary.volume = 1f

        if (toRelease != null) {
            toRelease.playWhenReady = false
            handler.post { toRelease.release() }
        }
    }

    fun release() {
        cancel()
        primary.removeListener(primaryListener)
    }
}
