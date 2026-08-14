package com.reomusic

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val musicProvider: MusicProvider =
        YouTubeMusicProvider()

    override fun onCreate() {

        super.onCreate()

        AppSettings.init(this)

        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(
                    C.AUDIO_CONTENT_TYPE_MUSIC
                )
                .build()

        player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    audioAttributes,
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build()

        val sessionActivityIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                sessionActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        mediaSession =
            MediaSession.Builder(
                this,
                player
            )
                .setSessionActivity(
                    pendingIntent
                )
                .build()
    }

    fun playQueue(
        tracks: List<MusicTrack>,
        startIndex: Int = 0
    ) {

        if (tracks.isEmpty()) {
            return
        }

        serviceScope.launch {

            val mediaItems =
                tracks.mapNotNull { track ->

                    try {

                        val streamUrl =
                            musicProvider
                                .getStreamUrl(
                                    track.videoId
                                )

                        if (streamUrl.isNullOrBlank()) {
                            return@mapNotNull null
                        }

                        MediaItem.Builder()
                            .setMediaId(
                                track.videoId
                            )
                            .setUri(streamUrl)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(
                                        track.title
                                    )
                                    .setArtist(
                                        track.artist
                                    )
                                    .setAlbumTitle(
                                        track.album
                                    )
                                    .setArtworkUri(
                                        android.net.Uri.parse(
                                            track.thumbnailUrl
                                        )
                                    )
                                    .build()
                            )
                            .build()

                    } catch (e: Exception) {

                        e.printStackTrace()

                        null
                    }
                }

            if (mediaItems.isEmpty()) {
                return@launch
            }

            val safeIndex =
                startIndex.coerceIn(
                    0,
                    mediaItems.lastIndex
                )

            launch(Dispatchers.Main) {

                player.setMediaItems(
                    mediaItems,
                    safeIndex,
                    0L
                )

                player.prepare()
                player.play()
            }
        }
    }

    fun addToQueue(
        track: MusicTrack
    ) {

        serviceScope.launch {

            try {

                val streamUrl =
                    musicProvider
                        .getStreamUrl(
                            track.videoId
                        )

                if (streamUrl.isNullOrBlank()) {
                    return@launch
                }

                val mediaItem =
                    MediaItem.Builder()
                        .setMediaId(
                            track.videoId
                        )
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(
                                    track.title
                                )
                                .setArtist(
                                    track.artist
                                )
                                .setAlbumTitle(
                                    track.album
                                )
                                .setArtworkUri(
                                    android.net.Uri.parse(
                                        track.thumbnailUrl
                                    )
                                )
                                .build()
                        )
                        .build()

                launch(Dispatchers.Main) {

                    player.addMediaItem(
                        mediaItem
                    )
                }

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession {

        return mediaSession
    }

    override fun onDestroy() {

        serviceScope.cancel()

        mediaSession.release()
        player.release()

        super.onDestroy()
    }
}
