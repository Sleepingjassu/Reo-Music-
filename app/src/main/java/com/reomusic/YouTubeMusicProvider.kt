package com.reomusic

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicSearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YouTubeMusicProvider : MusicProvider {

    private val client = OkHttpClient()

    init {
        NewPipeInitializer.initialize(client)
    }

    override suspend fun search(
        query: String
    ): List<MusicTrack> =
        withContext(Dispatchers.IO) {

            if (query.isBlank()) {
                return@withContext emptyList()
            }

            try {
                val service = YoutubeService(0)

                val queryHandler =
                    YoutubeSearchQueryHandlerFactory
                        .getInstance()
                        .fromQuery(
                            query,
                            listOf(
                                YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
                            ),
                            null
                        )

                val extractor =
                    YoutubeMusicSearchExtractor(
                        service,
                        queryHandler
                    )

                extractor.fetchPage()

                extractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { item ->

                        val videoId =
                            extractVideoId(item.url)

                        if (videoId.isBlank()) {
                            return@mapNotNull null
                        }

                        MusicTrack(
                            videoId = videoId,
                            title = item.name,
                            artist = item.uploaderName ?: "",
                            thumbnailUrl =
                                item.thumbnails
                                    .firstOrNull()
                                    ?.url
                                    ?: "",
                            durationSeconds =
                                item.duration
                        )
                    }

            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    override suspend fun getStreamUrl(
        videoId: String
    ): String? =
        withContext(Dispatchers.IO) {

            if (videoId.isBlank()) {
                return@withContext null
            }

            try {

                val videoUrl =
                    "https://www.youtube.com/watch?v=$videoId"

                val streamInfo =
                    StreamInfo.getInfo(
                        YoutubeService(0),
                        videoUrl
                    )

                /*
                 * Choose an audio-only stream.
                 *
                 * We deliberately avoid video streams because
                 * REO is an audio player. When Data Saver is on
                 * (see AppSettings), we pick the lowest-bitrate
                 * stream instead of the highest to save mobile data.
                 */
                val candidates =
                    streamInfo.audioStreams
                        .filter { stream ->
                            stream.content.isNotBlank()
                        }

                if (AppSettings.dataSaverEnabled) {
                    candidates.minByOrNull { stream -> stream.averageBitrate }
                        ?.content
                } else {
                    candidates.maxByOrNull { stream -> stream.averageBitrate }
                        ?.content
                }

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun extractVideoId(
        url: String
    ): String {

        return try {

            val uri = Uri.parse(url)

            uri.getQueryParameter("v")
                ?: uri.lastPathSegment
                ?: ""

        } catch (_: Exception) {
            ""
        }
    }
}

/**
 * Initializes NewPipe exactly once.
 */
private object NewPipeInitializer {

    private var initialized = false

    @Synchronized
    fun initialize(
        client: OkHttpClient
    ) {

        if (initialized) {
            return
        }

        NewPipe.init(
            OkHttpDownloader(client)
        )

        initialized = true
    }
}

/**
 * Downloader used by NewPipe.
 */
private class OkHttpDownloader(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(
        request: Request
    ): Response {

        val builder =
            OkHttpRequest.Builder()
                .url(request.url())

        request.headers()
            .forEach { (name, values) ->

                values.forEach { value ->

                    builder.addHeader(
                        name,
                        value
                    )
                }
            }

        if (request.httpMethod() == "POST") {

            val body =
                request.dataToSend()
                    ?.let {
                        okhttp3.RequestBody.create(
                            null,
                            it
                        )
                    }

            builder.post(
                body
                    ?: okhttp3.RequestBody.create(
                        null,
                        ByteArray(0)
                    )
            )

        } else {

            builder.method(
                request.httpMethod(),
                null
            )
        }

        client
            .newCall(builder.build())
            .execute()
            .use { response ->

                val headers =
                    response.headers.toMultimap()

                val body =
                    response.body
                        ?.string()
                        ?: ""

                return Response(
                    response.code,
                    response.message,
                    headers,
                    body,
                    response.request.url.toString()
                )
            }
    }
}
