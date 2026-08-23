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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var crossfadeEngine: CrossfadeEngine
    private lateinit var mediaSourceFactory: MediaSource.Factory
    private lateinit var audioAttributes: AudioAttributes

    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val tickHandler = Handler(Looper.getMainLooper())
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

    /** Drives the crossfade engine's fade timing independent of any UI being open. */
    private val tickCheck = object : Runnable {
        override fun run() {
            if (::crossfadeEngine.isInitialized) {
                crossfadeEngine.onPositionTick()
            }
            tickHandler.postDelayed(this, 250L)
        }
    }

    private val sleepTimerPlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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

        audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Every stream automatically flows through the shared disk cache,
        // so replays are instant/offline-capable without any extra work.
        mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(OfflineCacheManager.cacheDataSourceFactory())

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player.setSkipSilenceEnabled(AppSettings.skipSilenceEnabled)
        player.repeatMode = AppSettings.repeatMode
        player.setPlaybackSpeed(AppSettings.playbackSpeed)
        player.addListener(sleepTimerPlayerListener)

        crossfadeEngine = CrossfadeEngine(this, player, mediaSourceFactory, audioAttributes)

        val sessionActivityIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(ReoSessionCallback())
            .build()

        sleepTimerHandler.post(sleepTimerCheck)
        tickHandler.post(tickCheck)
    }

    private inner class ReoSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {

            val defaultResult = super.onConnect(session, controller)

            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(SleepTimerCommands.ACTION_SET_DURATION, Bundle.EMPTY))
                .add(SessionCommand(SleepTimerCommands.ACTION_SET_END_OF_TRACK, Bundle.EMPTY))
                .add(SessionCommand(SleepTimerCommands.ACTION_CANCEL, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_SKIP_SILENCE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_CROSSFADE_ENABLED, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_CROSSFADE_DURATION, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_PLAYBACK_SPEED, Bundle.EMPTY))
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

                ACTION_SET_SKIP_SILENCE -> {
                    val enabled = args.getBoolean(EXTRA_ENABLED, false)
                    AppSettings.setSkipSilenceEnabled(enabled)
                    player.setSkipSilenceEnabled(enabled)
                }

                ACTION_SET_CROSSFADE_ENABLED -> {
                    val enabled = args.getBoolean(EXTRA_ENABLED, false)
                    AppSettings.setCrossfadeEnabled(enabled)
                    if (!enabled) crossfadeEngine.cancel()
                }

                ACTION_SET_CROSSFADE_DURATION -> {
                    val ms = args.getInt(EXTRA_DURATION_MS, 4000)
                    AppSettings.setCrossfadeDurationMs(ms)
                }

                ACTION_SET_PLAYBACK_SPEED -> {
                    val speed = args.getFloat(EXTRA_SPEED, 1.0f)
                    AppSettings.setPlaybackSpeed(speed)
                    player.setPlaybackSpeed(speed)
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
        tickHandler.removeCallbacksAndMessages(null)
        if (::crossfadeEngine.isInitialized) crossfadeEngine.release()
        player.removeListener(sleepTimerPlayerListener)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SET_SKIP_SILENCE = "REO_SET_SKIP_SILENCE"
        const val ACTION_SET_CROSSFADE_ENABLED = "REO_SET_CROSSFADE_ENABLED"
        const val ACTION_SET_CROSSFADE_DURATION = "REO_SET_CROSSFADE_DURATION"
        const val ACTION_SET_PLAYBACK_SPEED = "REO_SET_PLAYBACK_SPEED"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_SPEED = "speed"
    }
}
