package com.reomusic

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
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
import kotlin.math.abs
import kotlin.math.max

private enum class Tab { HOME, SEARCH, LIBRARY, SETTINGS }
private enum class ListMode { QUEUE, FAVORITES, HISTORY, DOWNLOADS, PLAYLIST }
private enum class SortField { DEFAULT, TITLE, ARTIST, DURATION }

private const val MAX_QUEUE_LOOKAHEAD = 8
private const val SMART_SHUFFLE_MIN_UPCOMING = 3

class MainActivity : AppCompatActivity() {

    // Tabs / screens
    private lateinit var screenHome: View
    private lateinit var screenSearch: View
    private lateinit var screenLibrary: View
    private lateinit var screenSettings: View

    private lateinit var tabHome: LinearLayout
    private lateinit var tabSearch: LinearLayout
    private lateinit var tabLibrary: LinearLayout
    private lateinit var tabSettings: LinearLayout
    private lateinit var tabHomeIcon: ImageView
    private lateinit var tabSearchIcon: ImageView
    private lateinit var tabLibraryIcon: ImageView
    private lateinit var tabSettingsIcon: ImageView
    private lateinit var tabHomeLabel: TextView
    private lateinit var tabSearchLabel: TextView
    private lateinit var tabLibraryLabel: TextView
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
    private lateinit var npArtGestureArea: View
    private lateinit var npAlbumArt: ImageView
    private lateinit var npVisualizer: VisualizerBarView
    private lateinit var npTrackTitle: TextView
    private lateinit var npTrackArtist: TextView
    private lateinit var npProgressBar: SeekBar
    private lateinit var npTimeElapsed: TextView
    private lateinit var npTimeTotal: TextView
    private lateinit var npBtnPrev: ImageButton
    private lateinit var npBtnPlayPause: ImageButton
    private lateinit var npBtnNext: ImageButton
    private lateinit var npShuffleBtn: ImageButton
    private lateinit var npDownloadBtn: ImageButton
    private lateinit var npHeartBtn: ImageButton
    private lateinit var npSleepTimerBtn: ImageButton
    private lateinit var npEqualizerBtn: ImageButton
    private lateinit var npQueueHeader: View
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

    // Library screen
    private lateinit var libraryLikedRow: View
    private lateinit var libraryLikedCount: TextView
    private lateinit var libraryDownloadsRow: View
    private lateinit var libraryDownloadsCount: TextView
    private lateinit var libraryHistoryRow: View
    private lateinit var libraryHistoryCount: TextView
    private lateinit var libraryNewPlaylist: ImageButton
    private lateinit var libraryPlaylistsContainer: LinearLayout
    private lateinit var libraryPlaylistsEmpty: TextView

    // Generic list overlay
    private lateinit var listOverlay: View
    private lateinit var listBack: ImageButton
    private lateinit var listTitle: TextView
    private lateinit var listSubtitle: TextView
    private lateinit var listSort: ImageButton
    private lateinit var listClear: ImageButton
    private lateinit var listFilterWrap: View
    private lateinit var listFilterInput: EditText
    private lateinit var listActions: View
    private lateinit var listActionPlayAll: View
    private lateinit var listActionShuffle: View
    private lateinit var listContainer: LinearLayout
    private lateinit var listEmpty: TextView

    private var currentListMode: ListMode? = null
    private var currentPlaylistId: String? = null
    private var currentListTracks: List<MusicTrack> = emptyList()
    private var currentSortField = SortField.DEFAULT
    private var currentSortAscending = true

    // Equalizer overlay
    private lateinit var equalizerOverlay: View
    private lateinit var eqBack: ImageButton
    private lateinit var eqEnableSwitch: Switch
    private lateinit var eqPresetRow: LinearLayout
    private lateinit var eqBandsContainer: LinearLayout

    // Settings screen
    private lateinit var switchDataSaver: Switch
    private lateinit var switchKeepScreenOn: Switch
    private lateinit var switchSmartShuffle: Switch
    private lateinit var themeOptionSystem: TextView
    private lateinit var themeOptionLight: TextView
    private lateinit var themeOptionDark: TextView
    private lateinit var settingsEqualizerRow: View
    private lateinit var settingsClearCacheRow: View
    private lateinit var settingsCacheSize: TextView
    private lateinit var githubLink: View

    private var currentTab = Tab.HOME
    private var isNowPlayingOpen = false

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null

    private val musicProvider: MusicProvider = YouTubeMusicProvider()

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var playJob: Job? = null
    private var queueBuildJob: Job? = null
    private var homeLoadJob: Job? = null
    private var downloadJob: Job? = null
    private var pendingTrack: MusicTrack? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isDraggingProgress = false

    /** Player doesn't expose an audioSessionId getter — only the listener callback — so we cache it. */
    private var currentAudioSessionId: Int = 0

    /** videoId -> last resolved stream URL, so Download doesn't need a fresh network call. */
    private val resolvedStreamUrls = mutableMapOf<String, String>()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (currentAudioSessionId != 0) npVisualizer.start(currentAudioSessionId)
            }
        }

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

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            currentAudioSessionId = audioSessionId
            EqualizerManager.attach(audioSessionId)
            if (isNowPlayingOpen) {
                startVisualizerIfPermitted(audioSessionId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        AppSettings.init(this)
        applyThemeMode(AppSettings.themeMode)

        super.onCreate(savedInstanceState)

        PlayHistoryStore.init(this)
        FavoritesStore.init(this)
        PlaylistStore.init(this)
        DownloadStore.init(this)
        OfflineCacheManager.init(this)

        setContentView(R.layout.activity_main)

        bindViews()
        wireBottomNav()
        wireNowPlaying()
        wireSearchScreen()
        wireLibraryScreen()
        wireListOverlay()
        wireEqualizerScreen()
        wireSettingsScreen()
        wireMiniPlayer()

        requestRuntimePermissions()

        showTab(Tab.HOME)
        loadHomeContent()

        FavoritesStore.addListener(::onFavoritesChanged)
        PlaylistStore.addListener(::onPlaylistsChanged)

        connectToPlaybackService()
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun applyThemeMode(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun bindViews() {

        screenHome = findViewById(R.id.screen_home)
        screenSearch = findViewById(R.id.screen_search)
        screenLibrary = findViewById(R.id.screen_library)
        screenSettings = findViewById(R.id.screen_settings)

        tabHome = findViewById(R.id.tab_home)
        tabSearch = findViewById(R.id.tab_search)
        tabLibrary = findViewById(R.id.tab_library)
        tabSettings = findViewById(R.id.tab_settings)
        tabHomeIcon = findViewById(R.id.tab_home_icon)
        tabSearchIcon = findViewById(R.id.tab_search_icon)
        tabLibraryIcon = findViewById(R.id.tab_library_icon)
        tabSettingsIcon = findViewById(R.id.tab_settings_icon)
        tabHomeLabel = findViewById(R.id.tab_home_label)
        tabSearchLabel = findViewById(R.id.tab_search_label)
        tabLibraryLabel = findViewById(R.id.tab_library_label)
        tabSettingsLabel = findViewById(R.id.tab_settings_label)

        miniPlayer = findViewById(R.id.mini_player)
        miniArt = findViewById(R.id.mini_art)
        miniTitle = findViewById(R.id.mini_title)
        miniArtist = findViewById(R.id.mini_artist)
        miniPlayPause = findViewById(R.id.mini_play_pause)

        nowPlayingOverlay = findViewById(R.id.now_playing_overlay)
        nowPlayingDragArea = findViewById(R.id.now_playing_drag_area)
        nowPlayingCollapse = findViewById(R.id.now_playing_collapse)
        npArtGestureArea = findViewById(R.id.np_art_gesture_area)
        npAlbumArt = findViewById(R.id.np_album_art)
        npVisualizer = findViewById(R.id.np_visualizer)
        npTrackTitle = findViewById(R.id.np_track_title)
        npTrackArtist = findViewById(R.id.np_track_artist)
        npProgressBar = findViewById(R.id.np_progress_bar)
        npTimeElapsed = findViewById(R.id.np_time_elapsed)
        npTimeTotal = findViewById(R.id.np_time_total)
        npBtnPrev = findViewById(R.id.np_btn_prev)
        npBtnPlayPause = findViewById(R.id.np_btn_play_pause)
        npBtnNext = findViewById(R.id.np_btn_next)
        npShuffleBtn = findViewById(R.id.np_shuffle_btn)
        npDownloadBtn = findViewById(R.id.np_download_btn)
        npHeartBtn = findViewById(R.id.np_heart_btn)
        npSleepTimerBtn = findViewById(R.id.np_sleep_timer_btn)
        npEqualizerBtn = findViewById(R.id.np_equalizer_btn)
        npQueueHeader = findViewById(R.id.np_queue_header)
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

        libraryLikedRow = findViewById(R.id.library_liked_row)
        libraryLikedCount = findViewById(R.id.library_liked_count)
        libraryDownloadsRow = findViewById(R.id.library_downloads_row)
        libraryDownloadsCount = findViewById(R.id.library_downloads_count)
        libraryHistoryRow = findViewById(R.id.library_history_row)
        libraryHistoryCount = findViewById(R.id.library_history_count)
        libraryNewPlaylist = findViewById(R.id.library_new_playlist)
        libraryPlaylistsContainer = findViewById(R.id.library_playlists_container)
        libraryPlaylistsEmpty = findViewById(R.id.library_playlists_empty)

        listOverlay = findViewById(R.id.list_overlay)
        listBack = findViewById(R.id.list_back)
        listTitle = findViewById(R.id.list_title)
        listSubtitle = findViewById(R.id.list_subtitle)
        listSort = findViewById(R.id.list_sort)
        listClear = findViewById(R.id.list_clear)
        listFilterWrap = findViewById(R.id.list_filter_wrap)
        listFilterInput = findViewById(R.id.list_filter_input)
        listActions = findViewById(R.id.list_actions)
        listActionPlayAll = findViewById(R.id.list_action_play_all)
        listActionShuffle = findViewById(R.id.list_action_shuffle)
        listContainer = findViewById(R.id.list_container)
        listEmpty = findViewById(R.id.list_empty)

        equalizerOverlay = findViewById(R.id.equalizer_overlay)
        eqBack = findViewById(R.id.eq_back)
        eqEnableSwitch = findViewById(R.id.eq_enable_switch)
        eqPresetRow = findViewById(R.id.eq_preset_row)
        eqBandsContainer = findViewById(R.id.eq_bands_container)

        switchDataSaver = findViewById(R.id.switch_data_saver)
        switchKeepScreenOn = findViewById(R.id.switch_keep_screen_on)
        switchSmartShuffle = findViewById(R.id.switch_smart_shuffle)
        themeOptionSystem = findViewById(R.id.theme_option_system)
        themeOptionLight = findViewById(R.id.theme_option_light)
        themeOptionDark = findViewById(R.id.theme_option_dark)
        settingsEqualizerRow = findViewById(R.id.settings_equalizer_row)
        settingsClearCacheRow = findViewById(R.id.settings_clear_cache_row)
        settingsCacheSize = findViewById(R.id.settings_cache_size)
        githubLink = findViewById(R.id.github_link)
    }

    // ---------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------

    private fun wireBottomNav() {
        tabHome.setOnClickListener { showTab(Tab.HOME) }
        tabSearch.setOnClickListener { showTab(Tab.SEARCH) }
        tabLibrary.setOnClickListener { showTab(Tab.LIBRARY) }
        tabSettings.setOnClickListener { showTab(Tab.SETTINGS) }
    }

    private fun showTab(tab: Tab) {
        currentTab = tab

        screenHome.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        screenSearch.visibility = if (tab == Tab.SEARCH) View.VISIBLE else View.GONE
        screenLibrary.visibility = if (tab == Tab.LIBRARY) View.VISIBLE else View.GONE
        screenSettings.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE

        val accent = colorOf(R.color.accent)
        val muted = colorOf(R.color.text_muted)

        tabHomeIcon.setColorFilter(if (tab == Tab.HOME) accent else muted)
        tabSearchIcon.setColorFilter(if (tab == Tab.SEARCH) accent else muted)
        tabLibraryIcon.setColorFilter(if (tab == Tab.LIBRARY) accent else muted)
        tabSettingsIcon.setColorFilter(if (tab == Tab.SETTINGS) accent else muted)

        tabHomeLabel.setTextColor(if (tab == Tab.HOME) accent else muted)
        tabSearchLabel.setTextColor(if (tab == Tab.SEARCH) accent else muted)
        tabLibraryLabel.setTextColor(if (tab == Tab.LIBRARY) accent else muted)
        tabSettingsLabel.setTextColor(if (tab == Tab.SETTINGS) accent else muted)

        if (tab == Tab.HOME) loadHomeContent()
        if (tab == Tab.LIBRARY) loadLibraryContent()

        updateMiniPlayerVisibility()
    }

    private fun colorOf(resId: Int): Int = ContextCompat.getColor(this, resId)

    // ---------------------------------------------------------------
    // Now Playing overlay
    // ---------------------------------------------------------------

    private fun wireNowPlaying() {

        nowPlayingCollapse.setOnClickListener { closeNowPlaying() }

        npBtnPrev.setOnClickListener { mediaController?.seekToPreviousMediaItem() }
        npBtnNext.setOnClickListener { mediaController?.seekToNextMediaItem() }
        npBtnPlayPause.setOnClickListener { togglePlayPause() }

        npShuffleBtn.setOnClickListener {
            val enabled = !AppSettings.smartShuffleEnabled
            AppSettings.setSmartShuffleEnabled(enabled)
            switchSmartShuffle.isChecked = enabled
            refreshSmartShuffleIcon()
            if (enabled) maybeTopUpSmartQueue()
        }

        npHeartBtn.setOnClickListener {
            val track = currentPlayingTrack() ?: return@setOnClickListener
            val liked = FavoritesStore.toggle(track)
            refreshHeartIcon(liked)
        }

        npDownloadBtn.setOnClickListener { downloadCurrentTrack() }

        npSleepTimerBtn.setOnClickListener { showSleepTimerDialog() }

        npEqualizerBtn.setOnClickListener { openEqualizer() }

        npQueueHeader.setOnClickListener { openListScreen(ListMode.QUEUE) }

        npProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = mediaController?.duration ?: 0L
                    if (duration > 0) {
                        npTimeElapsed.text = formatTime((duration * progress) / 1000L)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { isDraggingProgress = true }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isDraggingProgress = false
                val controller = mediaController ?: return
                val duration = controller.duration
                if (duration > 0) {
                    controller.seekTo((duration * (seekBar?.progress ?: 0)) / 1000L)
                }
            }
        })

        // Drag-down-to-close on the header/handle.
        var dragStartY = 0f
        nowPlayingDragArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dragStartY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val delta = max(0f, event.rawY - dragStartY)
                    nowPlayingOverlay.translationY = delta
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (nowPlayingOverlay.translationY > 180f) {
                        closeNowPlaying()
                    } else {
                        nowPlayingOverlay.animate().translationY(0f).setDuration(160).start()
                    }
                    true
                }
                else -> false
            }
        }

        // Swipe-to-skip on the album art.
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val startX = e1?.x ?: return false
                val diffX = e2.x - startX
                if (abs(diffX) > 120 && abs(velocityX) > 300 && abs(diffX) > abs(e2.y - e1.y)) {
                    if (diffX < 0) mediaController?.seekToNextMediaItem() else mediaController?.seekToPreviousMediaItem()
                    return true
                }
                return false
            }
        })
        npArtGestureArea.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun openNowPlaying() {
        if (mediaController == null) return

        isNowPlayingOpen = true
        nowPlayingOverlay.translationY = nowPlayingOverlay.height.takeIf { it > 0 }?.toFloat() ?: 800f
        nowPlayingOverlay.alpha = 1f
        nowPlayingOverlay.visibility = View.VISIBLE

        nowPlayingOverlay.animate()
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()

        if (currentAudioSessionId != 0) startVisualizerIfPermitted(currentAudioSessionId)
        refreshHeartIcon(currentPlayingTrack()?.let { FavoritesStore.isLiked(it.videoId) } ?: false)
        refreshDownloadIcon()

        updateMiniPlayerVisibility()
    }

    private fun closeNowPlaying() {
        isNowPlayingOpen = false
        npVisualizer.stop()

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

    private fun startVisualizerIfPermitted(audioSessionId: Int) {
        val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            npVisualizer.start(audioSessionId)
        } else {
            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            equalizerOverlay.visibility == View.VISIBLE -> closeEqualizer()
            listOverlay.visibility == View.VISIBLE -> closeListScreen()
            isNowPlayingOpen -> closeNowPlaying()
            else -> super.onBackPressed()
        }
    }

    private fun updateMiniPlayerVisibility() {
        val hasTrack = mediaController?.currentMediaItem != null
        miniPlayer.visibility = if (hasTrack && !isNowPlayingOpen) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------
    // Mini player
    // ---------------------------------------------------------------

    private fun wireMiniPlayer() {
        miniPlayPause.setOnClickListener { togglePlayPause() }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                openNowPlaying()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val startX = e1?.x ?: return false
                val diffX = e2.x - startX
                if (abs(diffX) > 90 && abs(velocityX) > 250) {
                    if (diffX < 0) mediaController?.seekToNextMediaItem() else mediaController?.seekToPreviousMediaItem()
                    return true
                }
                return false
            }
        })
        miniPlayer.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
        refreshPlayPauseIcons(controller.isPlaying)
    }

    private fun currentPlayingTrack(): MusicTrack? {
        val item = mediaController?.currentMediaItem ?: return null
        return MusicTrack(
            videoId = item.mediaId,
            title = item.mediaMetadata.title?.toString() ?: "",
            artist = item.mediaMetadata.artist?.toString() ?: "",
            album = item.mediaMetadata.albumTitle?.toString() ?: "",
            thumbnailUrl = item.mediaMetadata.artworkUri?.toString() ?: ""
        )
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

        recent.take(10).forEach { track -> addRecentCard(track) }

        homePicksContainer.removeAllViews()

        homeLoadJob = screenScope.launch {
            val resolution = withContext(Dispatchers.IO) { musicProvider.resolveTrack(recent.first().videoId) }
            val picks = resolution?.relatedTracks.orEmpty().take(12)
            if (picks.isEmpty()) return@launch
            picks.forEach { track -> addTrackRow(homePicksContainer, track, showHeart = true, showDownload = true) }
        }
    }

    private fun addRecentCard(track: MusicTrack) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_recent_card, homeRecentContainer, false)
        val art = card.findViewById<ImageView>(R.id.recent_art)
        val title = card.findViewById<TextView>(R.id.recent_title)
        val artist = card.findViewById<TextView>(R.id.recent_artist)

        title.text = track.title
        artist.text = track.artist.ifBlank { "Unknown artist" }

        if (track.thumbnailUrl.isNotBlank()) {
            art.load(track.thumbnailUrl) {
                placeholder(R.drawable.bg_recent_card); error(R.drawable.bg_recent_card); crossfade(true)
            }
        }

        card.setOnClickListener { playTrack(track) }
        homeRecentContainer.addView(card)
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    private fun wireSearchScreen() {
        searchButton.setOnClickListener { search(searchInput.text.toString()) }
        searchInput.setOnEditorActionListener { _, _, _ -> search(searchInput.text.toString()); true }
    }

    private fun search(query: String) {
        if (query.isBlank()) return

        resultsContainer.removeAllViews()
        searchStatus.visibility = View.VISIBLE
        searchStatus.text = "Searching..."

        screenScope.launch {
            val results = withContext(Dispatchers.IO) { musicProvider.search(query) }

            if (results.isEmpty()) {
                searchStatus.text = "No results found"
                return@launch
            }

            searchStatus.visibility = View.GONE
            results.forEach { track -> addTrackRow(resultsContainer, track, showHeart = true, showDownload = true) }
        }
    }

    /** Generic track row used by Home picks, Search results, and various list screens. */
    private fun addTrackRow(
        container: LinearLayout,
        track: MusicTrack,
        showHeart: Boolean = false,
        showDownload: Boolean = false,
        showRemove: Boolean = false,
        onRemove: (() -> Unit)? = null
    ) {
        val item = LayoutInflater.from(this).inflate(R.layout.item_search_result, container, false)

        val art = item.findViewById<ImageView>(R.id.item_art)
        val title = item.findViewById<TextView>(R.id.item_title)
        val artist = item.findViewById<TextView>(R.id.item_artist)
        val play = item.findViewById<ImageButton>(R.id.item_play)
        val heart = item.findViewById<ImageButton>(R.id.item_heart)
        val download = item.findViewById<ImageButton>(R.id.item_download)
        val remove = item.findViewById<ImageButton>(R.id.item_remove)

        title.text = track.title
        artist.text = track.artist.ifBlank { "Unknown artist" }

        if (track.thumbnailUrl.isNotBlank()) {
            art.load(track.thumbnailUrl) {
                placeholder(R.drawable.bg_thumb_small); error(R.drawable.bg_thumb_small); crossfade(true)
            }
        }

        val onPlay = View.OnClickListener { playTrack(track) }
        play.setOnClickListener(onPlay)
        item.setOnClickListener(onPlay)

        if (showHeart) {
            heart.visibility = View.VISIBLE
            fun refreshHeart() {
                val liked = FavoritesStore.isLiked(track.videoId)
                heart.setImageResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                heart.setColorFilter(if (liked) colorOf(R.color.accent) else colorOf(R.color.text_muted))
            }
            refreshHeart()
            heart.setOnClickListener { FavoritesStore.toggle(track); refreshHeart() }
        }

        if (showDownload) {
            download.visibility = View.VISIBLE
            fun refreshDownload() {
                val done = DownloadStore.isDownloaded(track.videoId)
                download.setImageResource(if (done) R.drawable.ic_download_done else R.drawable.ic_download)
                download.setColorFilter(if (done) colorOf(R.color.accent) else colorOf(R.color.text_muted))
            }
            refreshDownload()
            download.setOnClickListener {
                if (!DownloadStore.isDownloaded(track.videoId)) {
                    downloadTrack(track) { refreshDownload() }
                } else {
                    Toast.makeText(this, "Already downloaded", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (showRemove) {
            remove.visibility = View.VISIBLE
            remove.setOnClickListener { onRemove?.invoke() }
        }

        container.addView(item)
    }

    // ---------------------------------------------------------------
    // Library
    // ---------------------------------------------------------------

    private fun wireLibraryScreen() {
        libraryLikedRow.setOnClickListener { openListScreen(ListMode.FAVORITES) }
        libraryDownloadsRow.setOnClickListener { openListScreen(ListMode.DOWNLOADS) }
        libraryHistoryRow.setOnClickListener { openListScreen(ListMode.HISTORY) }
        libraryNewPlaylist.setOnClickListener { showCreatePlaylistDialog(addTrackAfter = null) }
    }

    private fun loadLibraryContent() {
        libraryLikedCount.text = "${FavoritesStore.getAll().size} songs"
        libraryDownloadsCount.text = "${DownloadStore.getAll().size} songs"
        libraryHistoryCount.text = "${PlayHistoryStore.getRecent().size} songs"

        val playlists = PlaylistStore.getAll()
        libraryPlaylistsContainer.removeAllViews()
        libraryPlaylistsEmpty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE

        playlists.forEach { playlist ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_playlist_row, libraryPlaylistsContainer, false)
            row.findViewById<TextView>(R.id.playlist_row_name).text = playlist.name
            row.findViewById<TextView>(R.id.playlist_row_count).text = "${playlist.tracks.size} songs"
            row.setOnClickListener { openListScreen(ListMode.PLAYLIST, playlist.id) }
            row.findViewById<ImageButton>(R.id.playlist_row_delete).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete playlist?")
                    .setMessage("\u201c${playlist.name}\u201d will be removed.")
                    .setPositiveButton("Delete") { _, _ -> PlaylistStore.delete(playlist.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            libraryPlaylistsContainer.addView(row)
        }
    }

    private fun onFavoritesChanged() {
        if (currentTab == Tab.LIBRARY) loadLibraryContent()
        if (currentListMode == ListMode.FAVORITES) renderCurrentList()
    }

    private fun onPlaylistsChanged() {
        if (currentTab == Tab.LIBRARY) loadLibraryContent()
        if (currentListMode == ListMode.PLAYLIST) renderCurrentList()
    }

    private fun showCreatePlaylistDialog(addTrackAfter: MusicTrack?) {
        val input = EditText(this)
        input.hint = "Playlist name"
        input.setTextColor(colorOf(R.color.text_primary))
        input.setHintTextColor(colorOf(R.color.text_muted))
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)

        AlertDialog.Builder(this)
            .setTitle("New playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val playlist = PlaylistStore.create(input.text.toString())
                addTrackAfter?.let { PlaylistStore.addTrack(playlist.id, it) }
                loadLibraryContent()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddToPlaylistDialog(track: MusicTrack) {
        val playlists = PlaylistStore.getAll()
        val names = (playlists.map { it.name } + "+ New playlist").toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Add to playlist")
            .setItems(names) { _, which ->
                if (which == playlists.size) {
                    showCreatePlaylistDialog(addTrackAfter = track)
                } else {
                    PlaylistStore.addTrack(playlists[which].id, track)
                    Toast.makeText(this, "Added to ${playlists[which].name}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // ---------------------------------------------------------------
    // Generic list overlay: Queue / Favorites / History / Downloads / Playlist
    // ---------------------------------------------------------------

    private fun wireListOverlay() {
        listBack.setOnClickListener { closeListScreen() }

        listFilterInput.addTextChangedListener(SimpleTextWatcher { renderCurrentList() })

        listSort.setOnClickListener {
            currentSortField = when (currentSortField) {
                SortField.DEFAULT -> SortField.TITLE
                SortField.TITLE -> if (currentSortAscending) { currentSortAscending = false; SortField.TITLE }
                    else { currentSortAscending = true; SortField.ARTIST }
                SortField.ARTIST -> if (currentSortAscending) { currentSortAscending = false; SortField.ARTIST }
                    else { currentSortAscending = true; SortField.DURATION }
                SortField.DURATION -> if (currentSortAscending) { currentSortAscending = false; SortField.DURATION }
                    else { currentSortAscending = true; SortField.DEFAULT }
            }
            renderCurrentList()
        }

        listClear.setOnClickListener {
            when (currentListMode) {
                ListMode.HISTORY -> {
                    AlertDialog.Builder(this)
                        .setTitle("Clear history?")
                        .setPositiveButton("Clear") { _, _ ->
                            PlayHistoryStore.clearAll()
                            renderCurrentList()
                            if (currentTab == Tab.LIBRARY) loadLibraryContent()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> { /* not used for other modes */ }
            }
        }

        listActionPlayAll.setOnClickListener {
            if (currentListTracks.isNotEmpty()) playTrackListDirectly(currentListTracks, shuffled = false)
        }
        listActionShuffle.setOnClickListener {
            if (currentListTracks.isNotEmpty()) playTrackListDirectly(currentListTracks, shuffled = true)
        }
    }

    private fun openListScreen(mode: ListMode, playlistId: String? = null) {
        currentListMode = mode
        currentPlaylistId = playlistId
        currentSortField = SortField.DEFAULT
        currentSortAscending = true
        listFilterInput.setText("")

        listSort.visibility = View.VISIBLE
        listFilterWrap.visibility = View.VISIBLE
        listActions.visibility = View.VISIBLE
        listClear.visibility = View.GONE
        listSubtitle.visibility = View.GONE

        when (mode) {
            ListMode.QUEUE -> {
                listTitle.text = "Queue"
                listSort.visibility = View.GONE
                listFilterWrap.visibility = View.GONE
                listActions.visibility = View.GONE
            }
            ListMode.FAVORITES -> listTitle.text = "Liked Songs"
            ListMode.HISTORY -> {
                listTitle.text = "History"
                listClear.visibility = View.VISIBLE
                listActions.visibility = View.GONE
            }
            ListMode.DOWNLOADS -> listTitle.text = "Downloads"
            ListMode.PLAYLIST -> {
                val playlist = playlistId?.let { PlaylistStore.get(it) }
                listTitle.text = playlist?.name ?: "Playlist"
            }
        }

        renderCurrentList()

        listOverlay.visibility = View.VISIBLE
        listOverlay.alpha = 0f
        listOverlay.animate().alpha(1f).setDuration(160).start()
    }

    private fun closeListScreen() {
        listOverlay.animate().alpha(0f).setDuration(140).withEndAction {
            listOverlay.visibility = View.GONE
        }.start()
        currentListMode = null
        currentPlaylistId = null
    }

    private fun renderCurrentList() {
        val mode = currentListMode ?: return
        listContainer.removeAllViews()

        if (mode == ListMode.QUEUE) {
            renderQueueList()
            return
        }

        var tracks: List<MusicTrack> = when (mode) {
            ListMode.FAVORITES -> FavoritesStore.getAll()
            ListMode.HISTORY -> PlayHistoryStore.getRecent()
            ListMode.DOWNLOADS -> DownloadStore.getAll().map { it.track }
            ListMode.PLAYLIST -> currentPlaylistId?.let { PlaylistStore.get(it)?.tracks } ?: emptyList()
            ListMode.QUEUE -> emptyList()
        }

        val filter = listFilterInput.text.toString().trim()
        if (filter.isNotBlank()) {
            tracks = tracks.filter {
                it.title.contains(filter, ignoreCase = true) || it.artist.contains(filter, ignoreCase = true)
            }
        }

        tracks = when (currentSortField) {
            SortField.DEFAULT -> tracks
            SortField.TITLE -> tracks.sortedBy { it.title.lowercase() }
            SortField.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
            SortField.DURATION -> tracks.sortedBy { it.durationSeconds }
        }.let { if (!currentSortAscending && currentSortField != SortField.DEFAULT) it.reversed() else it }

        currentListTracks = tracks
        listSubtitle.visibility = if (currentSortField != SortField.DEFAULT) View.VISIBLE else View.GONE
        listSubtitle.text = "Sorted by ${currentSortField.name.lowercase().replaceFirstChar { it.uppercase() }} \u00b7 ${if (currentSortAscending) "A-Z" else "Z-A"}"

        listEmpty.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE

        tracks.forEach { track ->
            addTrackRow(
                listContainer, track,
                showHeart = mode != ListMode.FAVORITES,
                showDownload = mode != ListMode.DOWNLOADS,
                showRemove = true,
                onRemove = {
                    when (mode) {
                        ListMode.FAVORITES -> FavoritesStore.remove(track.videoId)
                        ListMode.DOWNLOADS -> {
                            DownloadStore.get(track.videoId)?.let { OfflineCacheManager.removeFromCache(it.streamUrl) }
                            DownloadStore.remove(track.videoId)
                        }
                        ListMode.PLAYLIST -> currentPlaylistId?.let { PlaylistStore.removeTrack(it, track.videoId) }
                        else -> {}
                    }
                    renderCurrentList()
                }
            )
        }
    }

    private fun renderQueueList() {
        val controller = mediaController
        listEmpty.visibility = View.GONE

        if (controller == null || controller.mediaItemCount == 0) {
            listEmpty.visibility = View.VISIBLE
            return
        }

        val currentIndex = controller.currentMediaItemIndex
        for (i in 0 until controller.mediaItemCount) {
            val item = controller.getMediaItemAt(i)
            val row = LayoutInflater.from(this).inflate(R.layout.item_queue_track, listContainer, false)

            val art = row.findViewById<ImageView>(R.id.queue_item_art)
            val title = row.findViewById<TextView>(R.id.queue_item_title)
            val artist = row.findViewById<TextView>(R.id.queue_item_artist)
            val up = row.findViewById<ImageButton>(R.id.queue_item_up)
            val down = row.findViewById<ImageButton>(R.id.queue_item_down)
            val remove = row.findViewById<ImageButton>(R.id.queue_item_remove)

            val displayTitle = item.mediaMetadata.title?.toString() ?: "Unknown"
            title.text = if (i == currentIndex) "\u25B6 $displayTitle" else displayTitle
            title.setTextColor(if (i == currentIndex) colorOf(R.color.accent) else colorOf(R.color.text_primary))
            artist.text = item.mediaMetadata.artist?.toString() ?: ""

            item.mediaMetadata.artworkUri?.let { uri ->
                art.load(uri) { placeholder(R.drawable.bg_thumb_small); error(R.drawable.bg_thumb_small); crossfade(true) }
            }

            up.setOnClickListener {
                if (i > 0) { controller.moveMediaItem(i, i - 1); renderQueueListRefresh() }
            }
            down.setOnClickListener {
                if (i < controller.mediaItemCount - 1) { controller.moveMediaItem(i, i + 1); renderQueueListRefresh() }
            }
            remove.setOnClickListener {
                if (i != currentIndex) { controller.removeMediaItem(i); renderQueueListRefresh() }
            }
            row.setOnClickListener { controller.seekTo(i, 0L); controller.play() }

            listContainer.addView(row)
        }
    }

    private fun renderQueueListRefresh() {
        listContainer.removeAllViews()
        renderQueueList()
        refreshQueueList()
    }

    // ---------------------------------------------------------------
    // Equalizer
    // ---------------------------------------------------------------

    private fun wireEqualizerScreen() {
        eqBack.setOnClickListener { closeEqualizer() }

        eqEnableSwitch.isChecked = AppSettings.equalizerEnabled
        eqEnableSwitch.setOnCheckedChangeListener { _, checked -> EqualizerManager.setEnabled(checked) }

        eqPresetRow.removeAllViews()
        EqualizerManager.presets.forEach { preset ->
            val chip = TextView(this).apply {
                text = preset
                textSize = 12f
                setTextColor(colorOf(R.color.text_primary))
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.selector_chip)
                isSelected = preset == AppSettings.equalizerPreset
                setOnClickListener {
                    EqualizerManager.applyPreset(preset)
                    refreshEqualizerPresetChips()
                    refreshEqualizerBandSliders()
                }
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.marginEnd = dp(8)
            chip.layoutParams = params
            eqPresetRow.addView(chip)
        }
    }

    private fun refreshEqualizerPresetChips() {
        for (i in 0 until eqPresetRow.childCount) {
            val chip = eqPresetRow.getChildAt(i) as? TextView ?: continue
            chip.isSelected = chip.text.toString() == AppSettings.equalizerPreset
        }
    }

    private fun refreshEqualizerBandSliders() {
        eqBandsContainer.removeAllViews()
        val bandCount = EqualizerManager.numberOfBands
        val range = EqualizerManager.bandLevelRange()

        for (band in 0 until bandCount) {
            val bandView = LayoutInflater.from(this).inflate(R.layout.item_eq_band, eqBandsContainer, false)
            val slider = bandView.findViewById<SeekBar>(R.id.eq_band_slider)
            val freqLabel = bandView.findViewById<TextView>(R.id.eq_band_freq)

            val min = range[0]
            val max = range[1]
            slider.max = max - min
            slider.progress = EqualizerManager.getBandLevel(band) - min

            val freqHz = EqualizerManager.getCenterFreqHz(band)
            freqLabel.text = if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz"

            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) EqualizerManager.setBandLevel(band, progress + min)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) { refreshEqualizerPresetChips() }
            })

            eqBandsContainer.addView(bandView)
        }
    }

    private fun openEqualizer() {
        eqEnableSwitch.isChecked = AppSettings.equalizerEnabled
        refreshEqualizerPresetChips()
        refreshEqualizerBandSliders()
        equalizerOverlay.visibility = View.VISIBLE
        equalizerOverlay.alpha = 0f
        equalizerOverlay.animate().alpha(1f).setDuration(160).start()
    }

    private fun closeEqualizer() {
        equalizerOverlay.animate().alpha(0f).setDuration(140).withEndAction {
            equalizerOverlay.visibility = View.GONE
        }.start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------
    // Sleep timer
    // ---------------------------------------------------------------

    private fun showSleepTimerDialog() {
        val options = arrayOf("15 minutes", "30 minutes", "45 minutes", "60 minutes", "End of current track", "Off")
        AlertDialog.Builder(this)
            .setTitle("Sleep timer")
            .setItems(options) { _, which ->
                val controller = mediaController ?: return@setItems
                when (which) {
                    0 -> sendSleepTimerDuration(controller, 15 * 60_000L)
                    1 -> sendSleepTimerDuration(controller, 30 * 60_000L)
                    2 -> sendSleepTimerDuration(controller, 45 * 60_000L)
                    3 -> sendSleepTimerDuration(controller, 60 * 60_000L)
                    4 -> controller.sendCustomCommand(SessionCommand(SleepTimerCommands.ACTION_SET_END_OF_TRACK, Bundle.EMPTY), Bundle.EMPTY)
                    5 -> controller.sendCustomCommand(SessionCommand(SleepTimerCommands.ACTION_CANCEL, Bundle.EMPTY), Bundle.EMPTY)
                }
                Toast.makeText(this, options[which], Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun sendSleepTimerDuration(controller: MediaController, durationMs: Long) {
        val args = Bundle().apply { putLong(SleepTimerCommands.EXTRA_DURATION_MS, durationMs) }
        controller.sendCustomCommand(SessionCommand(SleepTimerCommands.ACTION_SET_DURATION, Bundle.EMPTY), args)
    }

    // ---------------------------------------------------------------
    // Playback — fast single-track start + algorithmic "up next" queue
    // ---------------------------------------------------------------

    private fun playTrack(track: MusicTrack) {

        val controller = mediaController
        if (controller == null) {
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

            // Already downloaded? Skip the network round trip entirely.
            val downloaded = DownloadStore.get(track.videoId)
            if (downloaded != null) {
                val mediaItem = buildMediaItem(track, downloaded.streamUrl)
                controller.setMediaItems(listOf(mediaItem), 0, 0L)
                controller.prepare()
                controller.play()
                PlayHistoryStore.recordPlay(track)
                refreshNowPlayingMetadata()
                npQueueEmpty.text = "Offline track \u2014 no related songs available"
                return@launch
            }

            val resolution = withContext(Dispatchers.IO) { musicProvider.resolveTrack(track.videoId) }

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

    private fun buildUpNextQueue(seed: MusicTrack, related: List<MusicTrack>) {
        val controller = mediaController ?: return
        val candidates = related.filter { it.videoId != seed.videoId }.take(MAX_QUEUE_LOOKAHEAD)

        if (candidates.isEmpty()) {
            npQueueEmpty.text = "No related tracks found"
            return
        }

        queueBuildJob = screenScope.launch {
            var addedAny = false
            for (candidate in candidates) {
                val streamUrl = withContext(Dispatchers.IO) { musicProvider.getStreamUrl(candidate.videoId) }
                if (streamUrl.isNullOrBlank()) continue
                if (mediaController !== controller) return@launch

                controller.addMediaItem(buildMediaItem(candidate, streamUrl))
                addedAny = true
                refreshQueueList()
            }
            if (!addedAny) npQueueEmpty.text = "No related tracks found"
        }
    }

    /** Used by Play all / Shuffle on playlists, favorites, downloads, and history. */
    private fun playTrackListDirectly(tracks: List<MusicTrack>, shuffled: Boolean) {
        val controller = mediaController ?: return
        val ordered = if (shuffled) tracks.shuffled() else tracks
        val first = ordered.firstOrNull() ?: return
        val rest = ordered.drop(1)

        playJob?.cancel()
        queueBuildJob?.cancel()
        closeListScreen()
        playTrack(first)

        if (rest.isNotEmpty()) {
            queueBuildJob = screenScope.launch {
                for (candidate in rest.take(30)) {
                    val streamUrl = withContext(Dispatchers.IO) { musicProvider.getStreamUrl(candidate.videoId) }
                    if (streamUrl.isNullOrBlank()) continue
                    if (mediaController !== controller) return@launch
                    controller.addMediaItem(buildMediaItem(candidate, streamUrl))
                    refreshQueueList()
                }
            }
        }
    }

    /** Smart Shuffle: keeps the queue topped up with more related tracks as it runs low. */
    private fun maybeTopUpSmartQueue() {
        if (!AppSettings.smartShuffleEnabled) return
        val controller = mediaController ?: return
        if (queueBuildJob?.isActive == true) return

        val remaining = controller.mediaItemCount - controller.currentMediaItemIndex - 1
        if (remaining >= SMART_SHUFFLE_MIN_UPCOMING) return

        val lastItem = if (controller.mediaItemCount > 0) controller.getMediaItemAt(controller.mediaItemCount - 1) else return
        val seedId = lastItem.mediaId

        queueBuildJob = screenScope.launch {
            val resolution = withContext(Dispatchers.IO) { musicProvider.resolveTrack(seedId) }
            val extras = resolution?.relatedTracks.orEmpty().shuffled().take(MAX_QUEUE_LOOKAHEAD)
            for (candidate in extras) {
                val streamUrl = withContext(Dispatchers.IO) { musicProvider.getStreamUrl(candidate.videoId) }
                if (streamUrl.isNullOrBlank()) continue
                if (mediaController !== controller) return@launch
                controller.addMediaItem(buildMediaItem(candidate, streamUrl))
                refreshQueueList()
            }
        }
    }

    private fun buildMediaItem(track: MusicTrack, streamUrl: String): MediaItem {
        resolvedStreamUrls[track.videoId] = streamUrl
        return MediaItem.Builder()
            .setMediaId(track.videoId)
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null)
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
        } else {
            npQueueEmpty.visibility = View.GONE
            for (i in (currentIndex + 1) until minOf(count, currentIndex + 1 + 5)) {
                addQueuePreviewRow(controller.getMediaItemAt(i), i)
            }
        }

        maybeTopUpSmartQueue()

        if (currentListMode == ListMode.QUEUE) {
            listContainer.removeAllViews()
            renderQueueList()
        }
    }

    private fun addQueuePreviewRow(item: MediaItem, index: Int) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_queue_track, npQueueContainer, false)
        row.findViewById<ImageButton>(R.id.queue_item_up).visibility = View.GONE
        row.findViewById<ImageButton>(R.id.queue_item_down).visibility = View.GONE
        row.findViewById<ImageButton>(R.id.queue_item_remove).visibility = View.GONE

        val art = row.findViewById<ImageView>(R.id.queue_item_art)
        val title = row.findViewById<TextView>(R.id.queue_item_title)
        val artist = row.findViewById<TextView>(R.id.queue_item_artist)

        title.text = item.mediaMetadata.title?.toString() ?: "Unknown title"
        artist.text = item.mediaMetadata.artist?.toString() ?: ""

        item.mediaMetadata.artworkUri?.let { uri ->
            art.load(uri) { placeholder(R.drawable.bg_thumb_small); error(R.drawable.bg_thumb_small); crossfade(true) }
        }

        row.setOnClickListener { mediaController?.seekTo(index, 0L); mediaController?.play() }
        npQueueContainer.addView(row)
    }

    // ---------------------------------------------------------------
    // Downloads (offline caching)
    // ---------------------------------------------------------------

    private fun downloadCurrentTrack() {
        val track = currentPlayingTrack() ?: return
        val streamUrl = resolvedStreamUrls[track.videoId]

        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(this, "Track still loading, try again in a second", Toast.LENGTH_SHORT).show()
            return
        }

        if (DownloadStore.isDownloaded(track.videoId)) {
            Toast.makeText(this, "Already downloaded", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Downloading \u201c${track.title}\u201d...", Toast.LENGTH_SHORT).show()
        downloadJob?.cancel()
        downloadJob = screenScope.launch {
            val ok = withContext(Dispatchers.IO) { OfflineCacheManager.downloadFully(streamUrl) }
            if (ok) {
                DownloadStore.record(track, streamUrl)
                Toast.makeText(this@MainActivity, "Downloaded for offline playback", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Download failed", Toast.LENGTH_SHORT).show()
            }
            refreshDownloadIcon()
        }
    }

    private fun downloadTrack(track: MusicTrack, onDone: () -> Unit) {
        screenScope.launch {
            val resolution = withContext(Dispatchers.IO) { musicProvider.resolveTrack(track.videoId) }
            val streamUrl = resolution?.streamUrl
            if (streamUrl.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, "Couldn't download track", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(this@MainActivity, "Downloading \u201c${track.title}\u201d...", Toast.LENGTH_SHORT).show()
            val ok = withContext(Dispatchers.IO) { OfflineCacheManager.downloadFully(streamUrl) }
            if (ok) {
                DownloadStore.record(track, streamUrl)
                Toast.makeText(this@MainActivity, "Downloaded", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Download failed", Toast.LENGTH_SHORT).show()
            }
            onDone()
        }
    }

    private fun refreshDownloadIcon() {
        val track = currentPlayingTrack() ?: return
        val done = DownloadStore.isDownloaded(track.videoId)
        npDownloadBtn.setImageResource(if (done) R.drawable.ic_download_done else R.drawable.ic_download)
        npDownloadBtn.setColorFilter(if (done) colorOf(R.color.accent) else colorOf(R.color.text_muted))
    }

    private fun refreshHeartIcon(liked: Boolean) {
        npHeartBtn.setImageResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
        npHeartBtn.setColorFilter(if (liked) colorOf(R.color.accent) else colorOf(R.color.text_muted))
    }

    private fun refreshSmartShuffleIcon() {
        npShuffleBtn.setColorFilter(if (AppSettings.smartShuffleEnabled) colorOf(R.color.accent) else colorOf(R.color.text_muted))
    }

    // ---------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------

    private fun wireSettingsScreen() {

        switchDataSaver.isChecked = AppSettings.dataSaverEnabled
        switchKeepScreenOn.isChecked = AppSettings.keepScreenOnEnabled
        switchSmartShuffle.isChecked = AppSettings.smartShuffleEnabled

        switchDataSaver.setOnCheckedChangeListener { _, isChecked -> AppSettings.setDataSaverEnabled(isChecked) }
        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setKeepScreenOnEnabled(isChecked); applyKeepScreenOn()
        }
        switchSmartShuffle.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setSmartShuffleEnabled(isChecked)
            refreshSmartShuffleIcon()
            if (isChecked) maybeTopUpSmartQueue()
        }

        refreshThemeChips()
        themeOptionSystem.setOnClickListener { setThemeAndRecreate(ThemeMode.SYSTEM) }
        themeOptionLight.setOnClickListener { setThemeAndRecreate(ThemeMode.LIGHT) }
        themeOptionDark.setOnClickListener { setThemeAndRecreate(ThemeMode.DARK) }

        settingsEqualizerRow.setOnClickListener { openEqualizer() }

        refreshCacheSizeLabel()
        settingsClearCacheRow.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear playback cache?")
                .setMessage("This frees disk space used for instant replays. Downloaded songs in your Library are kept.")
                .setPositiveButton("Clear") { _, _ ->
                    Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
                    refreshCacheSizeLabel()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        githubLink.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sleepingjassu")))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun refreshThemeChips() {
        themeOptionSystem.isSelected = AppSettings.themeMode == ThemeMode.SYSTEM
        themeOptionLight.isSelected = AppSettings.themeMode == ThemeMode.LIGHT
        themeOptionDark.isSelected = AppSettings.themeMode == ThemeMode.DARK
    }

    private fun setThemeAndRecreate(mode: ThemeMode) {
        if (AppSettings.themeMode == mode) return
        AppSettings.setThemeMode(mode)
        applyThemeMode(mode)
        recreate()
    }

    private fun refreshCacheSizeLabel() {
        val bytes = OfflineCacheManager.currentCacheSizeBytes()
        val mb = bytes / (1024f * 1024f)
        settingsCacheSize.text = "%.1f MB used".format(mb)
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
                refreshSmartShuffleIcon()

                pendingTrack?.let { track -> pendingTrack = null; playTrack(track) }

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
                placeholder(R.drawable.bg_album_art_placeholder); error(R.drawable.bg_album_art_placeholder); crossfade(true)
            }
            miniArt.load(artworkUri) {
                placeholder(R.drawable.bg_thumb_small); error(R.drawable.bg_thumb_small); crossfade(true)
            }
        } else {
            npAlbumArt.setImageResource(R.drawable.bg_album_art_placeholder)
            miniArt.setImageResource(R.drawable.bg_thumb_small)
        }

        npTimeTotal.text = formatTime(controller.duration.coerceAtLeast(0L))

        refreshHeartIcon(FavoritesStore.isLiked(item.mediaId))
        refreshDownloadIcon()

        updateMiniPlayerVisibility()
    }

    private fun refreshPlayPauseIcons(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        npBtnPlayPause.setImageResource(icon)
        miniPlayPause.setImageResource(icon)
    }

    private fun refreshProgress() {
        val controller = mediaController ?: return
        if (isDraggingProgress) return

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
        if (currentTab == Tab.HOME) loadHomeContent()
        if (currentTab == Tab.LIBRARY) loadLibraryContent()
    }

    override fun onPause() {
        uiHandler.removeCallbacks(tickRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(tickRunnable)
        mediaController?.removeListener(playerListener)
        FavoritesStore.removeListener(::onFavoritesChanged)
        PlaylistStore.removeListener(::onPlaylistsChanged)
        EqualizerManager.release()
        npVisualizer.stop()
        screenScope.cancel()

        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }

        super.onDestroy()
    }
}

/** Minimal TextWatcher helper so call sites can pass a single lambda. */
private class SimpleTextWatcher(private val onChanged: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged() }
    override fun afterTextChanged(s: android.text.Editable?) {}
}
