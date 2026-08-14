package com.reomusic

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab { PLAYER, SEARCH, SETTINGS }

class MainActivity : Activity() {

    // Screens
    private lateinit var screenPlayer: View
    private lateinit var screenSearch: View
    private lateinit var screenSettings: View

    // Bottom nav
    private lateinit var tabPlayer: LinearLayout
    private lateinit var tabSearch: LinearLayout
    private lateinit var tabSettings: LinearLayout
    private lateinit var tabPlayerIcon: ImageView
    private lateinit var tabSearchIcon: ImageView
    private lateinit var tabSettingsIcon: ImageView
    private lateinit var tabPlayerLabel: TextView
    private lateinit var tabSearchLabel: TextView
    private lateinit var tabSettingsLabel: TextView

    // Mini player
    private lateinit var miniPlayer: View
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlayPause: ImageButton

    // Player screen
    private lateinit var albumArt: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var progressBar: SeekBar
    private lateinit var timeElapsed: TextView
    private lateinit var timeTotal: TextView
    private lateinit var volumeBar: SeekBar
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton

    // Search screen
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var searchStatus: TextView
    private lateinit var resultsContainer: LinearLayout

    // Settings screen
    private lateinit var switchDataSaver: Switch
    private lateinit var switchKeepScreenOn: Switch
    private lateinit var githubLink: View

    private var currentTab = Tab.PLAYER

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null

    private val musicProvider: MusicProvider = YouTubeMusicProvider()

    private val screenScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isDraggingProgress = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshProgress()
            applyKeepScreenOn()
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshPlayPauseIcons(isPlaying)
            applyKeepScreenOn()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshNowPlayingMetadata()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshNowPlayingMetadata()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppSettings.init(this)

        setContentView(R.layout.activity_main)

        bindViews()
        wireBottomNav()
        wirePlayerControls()
        wireSearchScreen()
        wireSettingsScreen()

        showTab(Tab.PLAYER)

        connectToPlaybackService()
    }

    private fun bindViews() {

        screenPlayer = findViewById(R.id.screen_player)
        screenSearch = findViewById(R.id.screen_search)
        screenSettings = findViewById(R.id.screen_settings)

        tabPlayer = findViewById(R.id.tab_player)
        tabSearch = findViewById(R.id.tab_search)
        tabSettings = findViewById(R.id.tab_settings)
        tabPlayerIcon = findViewById(R.id.tab_player_icon)
        tabSearchIcon = findViewById(R.id.tab_search_icon)
        tabSettingsIcon = findViewById(R.id.tab_settings_icon)
        tabPlayerLabel = findViewById(R.id.tab_player_label)
        tabSearchLabel = findViewById(R.id.tab_search_label)
        tabSettingsLabel = findViewById(R.id.tab_settings_label)

        miniPlayer = findViewById(R.id.mini_player)
        miniArt = findViewById(R.id.mini_art)
        miniTitle = findViewById(R.id.mini_title)
        miniArtist = findViewById(R.id.mini_artist)
        miniPlayPause = findViewById(R.id.mini_play_pause)

        val albumArtCard = findViewById<View>(R.id.album_art_card)
        albumArtCard.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width > 0 && width != height) {
                val params = view.layoutParams
                params.height = width
                view.layoutParams = params
            }
        }

        albumArt = findViewById(R.id.album_art)
        trackTitle = findViewById(R.id.track_title)
        trackArtist = findViewById(R.id.track_artist)
        progressBar = findViewById(R.id.progress_bar)
        timeElapsed = findViewById(R.id.time_elapsed)
        timeTotal = findViewById(R.id.time_total)
        volumeBar = findViewById(R.id.volume_bar)
        btnPrev = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)

        searchInput = findViewById(R.id.search_input)
        searchButton = findViewById(R.id.search_button)
        searchStatus = findViewById(R.id.search_status)
        resultsContainer = findViewById(R.id.results_container)

        switchDataSaver = findViewById(R.id.switch_data_saver)
        switchKeepScreenOn = findViewById(R.id.switch_keep_screen_on)
        githubLink = findViewById(R.id.github_link)
    }

    // ---------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------

    private fun wireBottomNav() {
        tabPlayer.setOnClickListener { showTab(Tab.PLAYER) }
        tabSearch.setOnClickListener { showTab(Tab.SEARCH) }
        tabSettings.setOnClickListener { showTab(Tab.SETTINGS) }
    }

    private fun showTab(tab: Tab) {
        currentTab = tab

        screenPlayer.visibility = if (tab == Tab.PLAYER) View.VISIBLE else View.GONE
        screenSearch.visibility = if (tab == Tab.SEARCH) View.VISIBLE else View.GONE
        screenSettings.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE

        val accent = colorOf(R.color.accent)
        val muted = colorOf(R.color.text_muted)

        tabPlayerIcon.setColorFilter(if (tab == Tab.PLAYER) accent else muted)
        tabSearchIcon.setColorFilter(if (tab == Tab.SEARCH) accent else muted)
        tabSettingsIcon.setColorFilter(if (tab == Tab.SETTINGS) accent else muted)

        tabPlayerLabel.setTextColor(if (tab == Tab.PLAYER) accent else muted)
        tabSearchLabel.setTextColor(if (tab == Tab.SEARCH) accent else muted)
        tabSettingsLabel.setTextColor(if (tab == Tab.SETTINGS) accent else muted)

        updateMiniPlayerVisibility()
    }

    private fun updateMiniPlayerVisibility() {
        val hasTrack = mediaController?.currentMediaItem != null
        miniPlayer.visibility =
            if (currentTab != Tab.PLAYER && hasTrack) View.VISIBLE else View.GONE
    }

    private fun colorOf(resId: Int): Int = ContextCompat.getColor(this, resId)

    // ---------------------------------------------------------------
    // Player controls
    // ---------------------------------------------------------------

    private fun wirePlayerControls() {

        btnPrev.setOnClickListener {
            mediaController?.seekToPreviousMediaItem()
        }

        btnNext.setOnClickListener {
            mediaController?.seekToNextMediaItem()
        }

        btnPlayPause.setOnClickListener { togglePlayPause() }
        miniPlayPause.setOnClickListener { togglePlayPause() }

        miniPlayer.setOnClickListener { showTab(Tab.PLAYER) }

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = mediaController?.duration ?: 0L
                    if (duration > 0) {
                        timeElapsed.text = formatTime((duration * progress) / 1000L)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isDraggingProgress = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isDraggingProgress = false
                val controller = mediaController ?: return
                val duration = controller.duration
                if (duration > 0) {
                    val targetMs = (duration * (seekBar?.progress ?: 0)) / 1000L
                    controller.seekTo(targetMs)
                }
            }
        })

        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaController?.volume = progress / 100f
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
        refreshPlayPauseIcons(controller.isPlaying)
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    private fun wireSearchScreen() {

        searchButton.setOnClickListener {
            search(searchInput.text.toString())
        }

        searchInput.setOnEditorActionListener { _, _, _ ->
            search(searchInput.text.toString())
            true
        }
    }

    private fun search(query: String) {

        if (query.isBlank()) {
            return
        }

        resultsContainer.removeAllViews()
        searchStatus.visibility = View.VISIBLE
        searchStatus.text = "Searching..."

        screenScope.launch {

            val results = withContext(Dispatchers.IO) {
                musicProvider.search(query)
            }

            if (results.isEmpty()) {
                searchStatus.text = "No results found"
                return@launch
            }

            searchStatus.visibility = View.GONE

            results.forEachIndexed { index, track ->
                addResultItem(track, results, index)
            }
        }
    }

    private fun addResultItem(track: MusicTrack, queue: List<MusicTrack>, index: Int) {

        val item = LayoutInflater.from(this)
            .inflate(R.layout.item_search_result, resultsContainer, false)

        val art = item.findViewById<ImageView>(R.id.item_art)
        val title = item.findViewById<TextView>(R.id.item_title)
        val artist = item.findViewById<TextView>(R.id.item_artist)
        val play = item.findViewById<ImageButton>(R.id.item_play)

        title.text = track.title
        artist.text = if (track.artist.isBlank()) "Unknown artist" else track.artist

        if (track.thumbnailUrl.isNotBlank()) {
            art.load(track.thumbnailUrl) {
                placeholder(R.drawable.bg_thumb_small)
                error(R.drawable.bg_thumb_small)
                crossfade(true)
            }
        }

        val onPlay = View.OnClickListener { playQueue(queue, index) }
        play.setOnClickListener(onPlay)
        item.setOnClickListener(onPlay)

        resultsContainer.addView(item)
    }

    private fun playQueue(tracks: List<MusicTrack>, startIndex: Int) {

        val controller = mediaController ?: return

        showTab(Tab.PLAYER)

        trackTitle.text = "Loading..."
        trackArtist.text = tracks.getOrNull(startIndex)?.artist ?: ""

        screenScope.launch {

            val mediaItems = tracks.mapNotNull { track ->
                try {
                    val streamUrl = withContext(Dispatchers.IO) {
                        musicProvider.getStreamUrl(track.videoId)
                    }

                    if (streamUrl.isNullOrBlank()) {
                        return@mapNotNull null
                    }

                    MediaItem.Builder()
                        .setMediaId(track.videoId)
                        .setUri(Uri.parse(streamUrl))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setAlbumTitle(track.album)
                                .setArtworkUri(
                                    if (track.thumbnailUrl.isNotBlank()) {
                                        Uri.parse(track.thumbnailUrl)
                                    } else {
                                        null
                                    }
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
                trackTitle.text = "Couldn't play track"
                trackArtist.text = "Try another song"
                return@launch
            }

            val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)

            controller.setMediaItems(mediaItems, safeIndex, 0L)
            controller.prepare()
            controller.play()

            refreshNowPlayingMetadata()
        }
    }

    // ---------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------

    private fun wireSettingsScreen() {

        switchDataSaver.isChecked = AppSettings.dataSaverEnabled
        switchKeepScreenOn.isChecked = AppSettings.keepScreenOnEnabled

        switchDataSaver.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setDataSaverEnabled(isChecked)
        }

        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setKeepScreenOnEnabled(isChecked)
            applyKeepScreenOn()
        }

        githubLink.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Sleepingjassu")
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyKeepScreenOn() {
        val shouldKeepOn = AppSettings.keepScreenOnEnabled && (mediaController?.isPlaying == true)
        if (shouldKeepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ---------------------------------------------------------------
    // Playback service connection
    // ---------------------------------------------------------------

    private fun connectToPlaybackService() {

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))

        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                mediaController?.addListener(playerListener)

                volumeBar.progress = ((mediaController?.volume ?: 1f) * 100).toInt()

                refreshNowPlayingMetadata()
                refreshPlayPauseIcons(mediaController?.isPlaying == true)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    // ---------------------------------------------------------------
    // UI refresh
    // ---------------------------------------------------------------

    private fun refreshNowPlayingMetadata() {

        val controller = mediaController ?: return
        val item = controller.currentMediaItem

        if (item == null) {
            trackTitle.text = "Nothing playing"
            trackArtist.text = "Search for a song to begin"
            miniTitle.text = "Nothing playing"
            miniArtist.text = ""
            albumArt.setImageDrawable(null)
            miniArt.setImageDrawable(null)
            updateMiniPlayerVisibility()
            return
        }

        val title = item.mediaMetadata.title?.toString() ?: "Unknown title"
        val artist = item.mediaMetadata.artist?.toString() ?: "Unknown artist"

        trackTitle.text = title
        trackArtist.text = artist
        miniTitle.text = title
        miniArtist.text = artist

        val artworkUri = item.mediaMetadata.artworkUri
        if (artworkUri != null) {
            albumArt.load(artworkUri) {
                placeholder(R.drawable.bg_album_art_placeholder)
                error(R.drawable.bg_album_art_placeholder)
                crossfade(true)
            }
            miniArt.load(artworkUri) {
                placeholder(R.drawable.bg_thumb_small)
                error(R.drawable.bg_thumb_small)
                crossfade(true)
            }
        } else {
            albumArt.setImageResource(R.drawable.bg_album_art_placeholder)
            miniArt.setImageResource(R.drawable.bg_thumb_small)
        }

        timeTotal.text = formatTime(controller.duration.coerceAtLeast(0L))

        updateMiniPlayerVisibility()
    }

    private fun refreshPlayPauseIcons(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnPlayPause.setImageResource(icon)
        miniPlayPause.setImageResource(icon)
    }

    private fun refreshProgress() {

        val controller = mediaController ?: return

        if (isDraggingProgress) {
            return
        }

        val duration = controller.duration
        val position = controller.currentPosition

        if (duration > 0) {
            progressBar.progress = ((position * 1000) / duration).toInt()
            timeElapsed.text = formatTime(position)
            timeTotal.text = formatTime(duration)
        }
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "0:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        uiHandler.post(tickRunnable)
        if (mediaController != null) {
            refreshNowPlayingMetadata()
            refreshPlayPauseIcons(mediaController?.isPlaying == true)
        }
    }

    override fun onPause() {
        uiHandler.removeCallbacks(tickRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(tickRunnable)
        mediaController?.removeListener(playerListener)
        screenScope.cancel()

        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }

        super.onDestroy()
    }
}
