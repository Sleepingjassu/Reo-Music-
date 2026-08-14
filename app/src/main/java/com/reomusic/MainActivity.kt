package com.reomusic

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private enum class Tab { HOME, SEARCH, SETTINGS }

/** How many related tracks we try to line up behind the current one. */
private const val MAX_QUEUE_LOOKAHEAD = 8

class MainActivity : Activity() {

    // Tabs / screens
    private lateinit var screenHome: View
    private lateinit var screenSearch: View
    private lateinit var screenSettings: View

    private lateinit var tabHome: LinearLayout
    private lateinit var tabSearch: LinearLayout
    private lateinit var tabSettings: LinearLayout
    private lateinit var tabHomeIcon: ImageView
    private lateinit var tabSearchIcon: ImageView
    private lateinit var tabSettingsIcon: ImageView
    private lateinit var tabHomeLabel: TextView
    private lateinit var tabSearchLabel: TextView
    private lateinit var tabSettingsLabel: TextView

    // Mini player
    private lateinit var miniPlayer: View
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlayPause: ImageButton

    // Now Playing overlay
    private lateinit var nowPlayingOverlay: View
    private lateinit var nowPlayingDragArea: View
    private lateinit var nowPlayingCollapse: ImageButton
    private lateinit var npAlbumArt: ImageView
    private lateinit var npTrackTitle: TextView
    private lateinit var npTrackArtist: TextView
    private lateinit var npProgressBar: SeekBar
    private lateinit var npTimeElapsed: TextView
    private lateinit var npTimeTotal: TextView
    private lateinit var npBtnPrev: ImageButton
    private lateinit var npBtnPlayPause: ImageButton
    private lateinit var npBtnNext: ImageButton
    private lateinit var npQueueContainer: LinearLayout
    private lateinit var npQueueEmpty: TextView

    // Home screen
    private lateinit var homeRecentSection: View
    private lateinit var homeRecentContainer: LinearLayout
    private lateinit var homePicksHeader: TextView
    private lateinit var homePicksContainer: LinearLayout
    private lateinit var homeEmptyState: View

    // Search screen
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var searchStatus: TextView
    private lateinit var resultsContainer: LinearLayout

    // Settings screen
    private lateinit var switchDataSaver: Switch
    private lateinit var switchKeepScreenOn: Switch
    private lateinit var githubLink: View

    private var currentTab = Tab.HOME
    private var isNowPlayingOpen = false

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null

    private val musicProvider: MusicProvider = YouTubeMusicProvider()

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cancellable jobs: starting a new track always kills the previous
    // in-flight work first, which is what fixes "can't play another song".
    private var playJob: Job? = null
    private var queueBuildJob: Job? = null
    private var homeLoadJob: Job? = null

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
            refreshQueueList()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            refreshQueueList()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshNowPlayingMetadata()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppSettings.init(this)
        PlayHistoryStore.init(this)

        setContentView(R.layout.activity_main)

        bindViews()
        wireBottomNav()
        wireNowPlaying()
        wireSearchScreen()
        wireSettingsScreen()
        wireMiniPlayer()

        showTab(Tab.HOME)
        loadHomeContent()

        connectToPlaybackService()
    }

    private fun bindViews() {

        screenHome = findViewById(R.id.screen_home)
        screenSearch = findViewById(R.id.screen_search)
        screenSettings = findViewById(R.id.screen_settings)

        tabHome = findViewById(R.id.tab_home)
        tabSearch = findViewById(R.id.tab_search)
        tabSettings = findViewById(R.id.tab_settings)
        tabHomeIcon = findViewById(R.id.tab_home_icon)
        tabSearchIcon = findViewById(R.id.tab_search_icon)
        tabSettingsIcon = findViewById(R.id.tab_settings_icon)
        tabHomeLabel = findViewById(R.id.tab_home_label)
        tabSearchLabel = findViewById(R.id.tab_search_label)
        tabSettingsLabel = findViewById(R.id.tab_settings_label)

        miniPlayer = findViewById(R.id.mini_player)
        miniArt = findViewById(R.id.mini_art)
        miniTitle = findViewById(R.id.mini_title)
        miniArtist = findViewById(R.id.mini_artist)
        miniPlayPause = findViewById(R.id.mini_play_pause)

        nowPlayingOverlay = findViewById(R.id.now_playing_overlay)
        nowPlayingDragArea = findViewById(R.id.now_playing_drag_area)
        nowPlayingCollapse = findViewById(R.id.now_playing_collapse)
        npAlbumArt = findViewById(R.id.np_album_art)
        npTrackTitle = findViewById(R.id.np_track_title)
        npTrackArtist = findViewById(R.id.np_track_artist)
        npProgressBar = findViewById(R.id.np_progress_bar)
        npTimeElapsed = findViewById(R.id.np_time_elapsed)
        npTimeTotal = findViewById(R.id.np_time_total)
        npBtnPrev = findViewById(R.id.np_btn_prev)
        npBtnPlayPause = findViewById(R.id.np_btn_play_pause)
        npBtnNext = findViewById(R.id.np_btn_next)
        npQueueContainer = findViewById(R.id.np_queue_container)
        npQueueEmpty = findViewById(R.id.np_queue_empty)

        val albumArtCard = findViewById<View>(R.id.np_album_art_card)
        albumArtCard.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width > 0 && width != height) {
                val params = view.layoutParams
                params.height = width
                view.layoutParams = params
            }
        }

        homeRecentSection = findViewById(R.id.home_recent_section)
        homeRecentContainer = findViewById(R.id.home_recent_container)
        homePicksHeader = findViewById(R.id.home_picks_header)
        homePicksContainer = findViewById(R.id.home_picks_container)
        homeEmptyState = findViewById(R.id.home_empty_state)

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
        tabHome.setOnClickListener { showTab(Tab.HOME) }
        tabSearch.setOnClickListener { showTab(Tab.SEARCH) }
        tabSettings.setOnClickListener { showTab(Tab.SETTINGS) }
    }

    private fun showTab(tab: Tab) {
        currentTab = tab

        screenHome.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        screenSearch.visibility = if (tab == Tab.SEARCH) View.VISIBLE else View.GONE
        screenSettings.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE

        val accent = colorOf(R.color.accent)
        val muted = colorOf(R.color.text_muted)

        tabHomeIcon.setColorFilter(if (tab == Tab.HOME) accent else muted)
        tabSearchIcon.setColorFilter(if (tab == Tab.SEARCH) accent else muted)
        tabSettingsIcon.setColorFilter(if (tab == Tab.SETTINGS) accent else muted)

        tabHomeLabel.setTextColor(if (tab == Tab.HOME) accent else muted)
        tabSearchLabel.setTextColor(if (tab == Tab.SEARCH) accent else muted)
        tabSettingsLabel.setTextColor(if (tab == Tab.SETTINGS) accent else muted)

        if (tab == Tab.HOME) {
            loadHomeContent()
        }

        updateMiniPlayerVisibility()
    }

    private fun colorOf(resId: Int): Int = ContextCompat.getColor(this, resId)

    // ---------------------------------------------------------------
    // Now Playing overlay (mini player -> full screen sheet)
    // ---------------------------------------------------------------

    private fun wireNowPlaying() {

        nowPlayingCollapse.setOnClickListener { closeNowPlaying() }

        npBtnPrev.setOnClickListener { mediaController?.seekToPreviousMediaItem() }
        npBtnNext.setOnClickListener { mediaController?.seekToNextMediaItem() }
        npBtnPlayPause.setOnClickListener { togglePlayPause() }

        npProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = mediaController?.duration ?: 0L
                    if (duration > 0) {
                        npTimeElapsed.text = formatTime((duration * progress) / 1000L)
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

        // Simple drag-to-dismiss on the handle area at the top of the sheet.
        var dragStartY = 0f
        nowPlayingDragArea.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = max(0f, event.rawY - dragStartY)
                    nowPlayingOverlay.translationY = delta
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val delta = nowPlayingOverlay.translationY
                    if (delta > 180f) {
                        closeNowPlaying()
                    } else {
                        nowPlayingOverlay.animate()
                            .translationY(0f)
                            .setDuration(160)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openNowPlaying() {

        if (mediaController == null) {
            return
        }

        isNowPlayingOpen = true
        nowPlayingOverlay.translationY = nowPlayingOverlay.height.takeIf { it > 0 }?.toFloat() ?: 800f
        nowPlayingOverlay.alpha = 1f
        nowPlayingOverlay.visibility = View.VISIBLE

        nowPlayingOverlay.animate()
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()

        updateMiniPlayerVisibility()
    }

    private fun closeNowPlaying() {

        isNowPlayingOpen = false

        nowPlayingOverlay.animate()
            .translationY(nowPlayingOverlay.height.toFloat())
            .setDuration(200)
            .withEndAction {
                nowPlayingOverlay.visibility = View.GONE
                nowPlayingOverlay.translationY = 0f
            }
            .start()

        updateMiniPlayerVisibility()
    }

    override fun onBackPressed() {
        if (isNowPlayingOpen) {
            closeNowPlaying()
        } else {
            super.onBackPressed()
        }
    }

    private fun updateMiniPlayerVisibility() {
        val hasTrack = mediaController?.currentMediaItem != null
        miniPlayer.visibility =
            if (hasTrack && !isNowPlayingOpen) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------
    // Mini player
    // ---------------------------------------------------------------

    private fun wireMiniPlayer() {
        miniPlayer.setOnClickListener { openNowPlaying() }
        miniPlayPause.setOnClickListener { togglePlayPause() }
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
    // Home
    // ---------------------------------------------------------------

    private fun loadHomeContent() {

        homeLoadJob?.cancel()

        val recent = PlayHistoryStore.getRecent()

        homeRecentContainer.removeAllViews()

        if (recent.isEmpty()) {
            homeRecentSection.visibility = View.GONE
            homePicksHeader.visibility = View.GONE
            homePicksContainer.removeAllViews()
            homeEmptyState.visibility = View.VISIBLE
            return
        }

        homeEmptyState.visibility = View.GONE
        homeRecentSection.visibility = View.VISIBLE
        homePicksHeader.visibility = View.VISIBLE
        homePicksHeader.text = "Because you played \u201c${recent.first().title}\u201d"

        recent.take(10).forEach { track ->
            addRecentCard(track)
        }

        homePicksContainer.removeAllViews()

        homeLoadJob = screenScope.launch {
            val resolution = withContext(Dispatchers.IO) {
                musicProvider.resolveTrack(recent.first().videoId)
            }

            val picks = resolution?.relatedTracks.orEmpty().take(12)

            if (picks.isEmpty()) {
                return@launch
            }

            picks.forEach { track ->
                addSearchResultRow(homePicksContainer, track, picks)
            }
        }
    }

    private fun addRecentCard(track: MusicTrack) {

        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_recent_card, homeRecentContainer, false)

        val art = card.findViewById<ImageView>(R.id.recent_art)
        val title = card.findViewById<TextView>(R.id.recent_title)
        val artist = card.findViewById<TextView>(R.id.recent_artist)

        title.text = track.title
        artist.text = track.artist.ifBlank { "Unknown artist" }

        if (track.thumbnailUrl.isNotBlank()) {
            art.load(track.thumbnailUrl) {
                placeholder(R.drawable.bg_recent_card)
                error(R.drawable.bg_recent_card)
                crossfade(true)
            }
        }

        card.setOnClickListener { playTrack(track) }
        homeRecentContainer.addView(card)
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

            results.forEach { track ->
                addSearchResultRow(resultsContainer, track, results)
            }
        }
    }

    private fun addSearchResultRow(
        container: LinearLayout,
        track: MusicTrack,
        contextList: List<MusicTrack>
    ) {

        val item = LayoutInflater.from(this)
            .inflate(R.layout.item_search_result, container, false)

        val art = item.findViewById<ImageView>(R.id.item_art)
        val title = item.findViewById<TextView>(R.id.item_title)
        val artist = item.findViewById<TextView>(R.id.item_artist)
        val play = item.findViewById<ImageButton>(R.id.item_play)

        title.text = track.title
        artist.text = track.artist.ifBlank { "Unknown artist" }

        if (track.thumbnailUrl.isNotBlank()) {
            art.load(track.thumbnailUrl) {
                placeholder(R.drawable.bg_thumb_small)
                error(R.drawable.bg_thumb_small)
                crossfade(true)
            }
        }

        val onPlay = View.OnClickListener { playTrack(track) }
        play.setOnClickListener(onPlay)
        item.setOnClickListener(onPlay)

        container.addView(item)
    }

    // ---------------------------------------------------------------
    // Playback — fast single-track start + algorithmic "up next" queue
    // ---------------------------------------------------------------

    /**
     * Plays [track] immediately: resolves only this one track's stream
     * (a single network round trip, so playback starts in ~1-2s instead
     * of waiting on an entire search result list), then, in the
     * background, builds an "up next" queue from tracks related to it —
     * similar to YouTube Music's autoplay radio — without blocking
     * playback or the UI.
     *
     * Any previous in-flight play/queue job is cancelled first, which is
     * what prevents a slow older request from overwriting a newer pick.
     */
    private var pendingTrack: MusicTrack? = null

    private fun playTrack(track: MusicTrack) {

        val controller = mediaController
        if (controller == null) {
            // Service connection may still be warming up on a cold start —
            // remember the request and fire it as soon as it's ready.
            pendingTrack = track
            npTrackTitle.text = track.title
            npTrackArtist.text = "Connecting..."
            return
        }

        playJob?.cancel()
        queueBuildJob?.cancel()

        openNowPlaying()

        npTrackTitle.text = track.title
        npTrackArtist.text = track.artist.ifBlank { "Loading..." }
        miniTitle.text = track.title
        miniArtist.text = track.artist

        npQueueContainer.removeAllViews()
        npQueueEmpty.visibility = View.VISIBLE
        npQueueEmpty.text = "Building your queue..."

        playJob = screenScope.launch {

            val resolution = withContext(Dispatchers.IO) {
                musicProvider.resolveTrack(track.videoId)
            }

            if (resolution == null) {
                npTrackTitle.text = "Couldn't play track"
                npTrackArtist.text = "Try another song"
                return@launch
            }

            val mediaItem = buildMediaItem(track, resolution.streamUrl)

            controller.setMediaItems(listOf(mediaItem), 0, 0L)
            controller.prepare()
            controller.play()

            PlayHistoryStore.recordPlay(track)

            refreshNowPlayingMetadata()

            buildUpNextQueue(track, resolution.relatedTracks)
        }
    }

    /**
     * Progressively resolves related tracks in the background and appends
     * each one to the live queue as soon as it's ready, so the player
     * never blocks on the whole batch — the up-next list just fills in.
     */
    private fun buildUpNextQueue(seed: MusicTrack, related: List<MusicTrack>) {

        val controller = mediaController ?: return

        val candidates = related
            .filter { it.videoId != seed.videoId }
            .take(MAX_QUEUE_LOOKAHEAD)

        if (candidates.isEmpty()) {
            npQueueEmpty.text = "No related tracks found"
            return
        }

        queueBuildJob = screenScope.launch {

            var addedAny = false

            for (candidate in candidates) {

                val streamUrl = withContext(Dispatchers.IO) {
                    musicProvider.getStreamUrl(candidate.videoId)
                }

                if (streamUrl.isNullOrBlank()) {
                    continue
                }

                // Bail out cleanly if a newer track was chosen meanwhile.
                if (mediaController !== controller) {
                    return@launch
                }

                controller.addMediaItem(buildMediaItem(candidate, streamUrl))
                addedAny = true
                refreshQueueList()
            }

            if (!addedAny) {
                npQueueEmpty.text = "No related tracks found"
            }
        }
    }

    private fun buildMediaItem(track: MusicTrack, streamUrl: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.videoId)
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(
                        if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null
                    )
                    .build()
            )
            .build()
    }

    private fun refreshQueueList() {

        val controller = mediaController ?: return

        npQueueContainer.removeAllViews()

        val count = controller.mediaItemCount
        val currentIndex = controller.currentMediaItemIndex

        if (count <= currentIndex + 1) {
            npQueueEmpty.visibility = View.VISIBLE
            return
        }

        npQueueEmpty.visibility = View.GONE

        for (i in (currentIndex + 1) until count) {
            val item = controller.getMediaItemAt(i)
            addQueueRow(item, i)
        }
    }

    private fun addQueueRow(item: MediaItem, index: Int) {

        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_queue_track, npQueueContainer, false)

        val art = row.findViewById<ImageView>(R.id.queue_item_art)
        val title = row.findViewById<TextView>(R.id.queue_item_title)
        val artist = row.findViewById<TextView>(R.id.queue_item_artist)

        title.text = item.mediaMetadata.title?.toString() ?: "Unknown title"
        artist.text = item.mediaMetadata.artist?.toString() ?: ""

        val artworkUri = item.mediaMetadata.artworkUri
        if (artworkUri != null) {
            art.load(artworkUri) {
                placeholder(R.drawable.bg_thumb_small)
                error(R.drawable.bg_thumb_small)
                crossfade(true)
            }
        }

        row.setOnClickListener {
            mediaController?.seekTo(index, 0L)
            mediaController?.play()
        }

        npQueueContainer.addView(row)
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
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sleepingjassu"))
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

                refreshNowPlayingMetadata()
                refreshPlayPauseIcons(mediaController?.isPlaying == true)
                refreshQueueList()

                pendingTrack?.let { track ->
                    pendingTrack = null
                    playTrack(track)
                }

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
            npTrackTitle.text = "Nothing playing"
            npTrackArtist.text = "Search for a song to begin"
            miniTitle.text = "Nothing playing"
            miniArtist.text = ""
            npAlbumArt.setImageDrawable(null)
            updateMiniPlayerVisibility()
            return
        }

        val title = item.mediaMetadata.title?.toString() ?: "Unknown title"
        val artist = item.mediaMetadata.artist?.toString() ?: "Unknown artist"

        npTrackTitle.text = title
        npTrackArtist.text = artist
        miniTitle.text = title
        miniArtist.text = artist

        val artworkUri = item.mediaMetadata.artworkUri
        if (artworkUri != null) {
            npAlbumArt.load(artworkUri) {
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
            npAlbumArt.setImageResource(R.drawable.bg_album_art_placeholder)
            miniArt.setImageResource(R.drawable.bg_thumb_small)
        }

        npTimeTotal.text = formatTime(controller.duration.coerceAtLeast(0L))

        updateMiniPlayerVisibility()
    }

    private fun refreshPlayPauseIcons(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        npBtnPlayPause.setImageResource(icon)
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
            npProgressBar.progress = ((position * 1000) / duration).toInt()
            npTimeElapsed.text = formatTime(position)
            npTimeTotal.text = formatTime(duration)
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
        if (currentTab == Tab.HOME) {
            loadHomeContent()
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
