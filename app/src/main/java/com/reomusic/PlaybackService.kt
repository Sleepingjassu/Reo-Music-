package com.reomusic

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val transitionHandler = Handler(Looper.getMainLooper())
    private var sleepTimerEndAtMillis: Long? = null
    private var sleepTimerEndOfTrack: Boolean = false

    private val sleepTimerCheck = object : Runnable {
        override fun run() {
            val endAt = sleepTimerEndAtMillis
            if (endAt != null && System.currentTimeMillis() >= endAt) {
                player.pause()
                sleepTimerEndAtMillis = null
            }
            sleepTimerHandler.postDelayed(this, 10_000L)
        }
    }

    private val sleepTimerPlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyTransitionFadeIn()
            if (sleepTimerEndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                player.pause()
                sleepTimerEndOfTrack = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        AppSettings.init(this)
        OfflineCacheManager.init(this)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Every stream automatically flows through the shared disk cache,
        // so replays are instant/offline-capable without any extra work.
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(OfflineCacheManager.cacheDataSourceFactory())

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.setSkipSilenceEnabled(AppSettings.skipSilenceEnabled)
        player.addListener(sleepTimerPlayerListener)

        val sessionActivityIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(SleepTimerSessionCallback())
            .build()

        sleepTimerHandler.post(sleepTimerCheck)
    }


    /**
     * Media3 handles playlist/gapless transitions. REO keeps the optional
     * transition setting deliberately conservative here: a short fade-in on
     * the newly selected item avoids the ugly hard-start without pretending a
     * single ExoPlayer can overlap two streams. A true dual-player crossfade
     * belongs in a dedicated audio mixer layer.
     */
    private fun applyTransitionFadeIn() {
        if (!AppSettings.crossfadeEnabled || !player.isPlaying) return
        player.volume = 0f
        val start = System.currentTimeMillis()
        fun step() {
            val t = ((System.currentTimeMillis() - start).toFloat() / 900L).coerceIn(0f, 1f)
            player.volume = t
            if (t < 1f) transitionHandler.postDelayed({ step() }, 35L)
        }
        step()
    }

    private inner class SleepTimerSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {

            val defaultResult = super.onConnect(session, controller)

            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(SleepTimerCommands.ACTION_SET_DURATION, Bundle.EMPTY))
                .add(SessionCommand(SleepTimerCommands.ACTION_SET_END_OF_TRACK, Bundle.EMPTY))
                .add(SessionCommand(SleepTimerCommands.ACTION_CANCEL, Bundle.EMPTY))
                .add(SessionCommand("REO_SET_SKIP_SILENCE", Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                defaultResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<androidx.media3.session.SessionResult> {

            when (customCommand.customAction) {
                "REO_SET_SKIP_SILENCE" -> {
                    val enabled = args.getBoolean("enabled", false)
                    AppSettings.setSkipSilenceEnabled(enabled)
                    player.setSkipSilenceEnabled(enabled)
                }

                SleepTimerCommands.ACTION_SET_DURATION -> {
                    val durationMs = args.getLong(SleepTimerCommands.EXTRA_DURATION_MS, 0L)
                    sleepTimerEndOfTrack = false
                    sleepTimerEndAtMillis = if (durationMs > 0) {
                        System.currentTimeMillis() + durationMs
                    } else null
                }

                SleepTimerCommands.ACTION_SET_END_OF_TRACK -> {
                    sleepTimerEndAtMillis = null
                    sleepTimerEndOfTrack = true
                }

                SleepTimerCommands.ACTION_CANCEL -> {
                    sleepTimerEndAtMillis = null
                    sleepTimerEndOfTrack = false
                }
            }

            return Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        sleepTimerHandler.removeCallbacksAndMessages(null)
        transitionHandler.removeCallbacksAndMessages(null)
        player.removeListener(sleepTimerPlayerListener)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}
