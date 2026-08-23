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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.google.android.material.color.DynamicColors
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

private const val MAX_QUEUE_LOOKAHEAD = 20
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
    private lateinit var npLyricsBtn: ImageButton

    private lateinit var lyricsOverlay: View
    private lateinit var lyricsBack: ImageButton
    private lateinit var lyricsTitle: TextView
    private lateinit var lyricsArtist: TextView
    private lateinit var lyricsText: TextView
    private lateinit var lyricsScroll: android.widget.ScrollView
    private lateinit var npQueueHeader: View
    private lateinit var npQueueContainer: LinearLayout
    private lateinit var npQueueEmpty: TextView

    // Home screen
    private lateinit var homeGreeting: TextView
    private lateinit var homeRecentSection: View
    private lateinit var homeRecentContainer: LinearLayout
    private lateinit var homePicksSection: View
    private lateinit var homePicksHeader: TextView
    private lateinit var homePicksContainer: LinearLayout
    private lateinit var homeDiscoverContainer: LinearLayout
    private lateinit var homeEmptyState: View
    private lateinit var homeHero: View
    private lateinit var homeHeroArt: ImageView
    private lateinit var homeHeroTitle: TextView
    private lateinit var homeHeroArtist: TextView
    private lateinit var homeHeroPlay: View
    private lateinit var homeSearchShortcut: ImageButton
    private lateinit var homeSettingsShortcut: ImageButton
    private lateinit var homeChipMadeForYou: TextView
    private lateinit var homeChipDiscover: TextView
    private lateinit var homeChipMoods: TextView
    private lateinit var homeChipCharts: TextView

    // Search screen
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var searchClearButton: ImageButton
    private lateinit var searchStatus: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var searchRecentSection: View
    private lateinit var searchRecentContainer: LinearLayout
    private lateinit var searchClearHistory: TextView
    private lateinit var searchFilterAll: TextView
    private lateinit var searchFilterSongs: TextView
    private lateinit var searchFilterArtists: TextView
    private lateinit var searchFilterAlbums: TextView
    private lateinit var searchFilterPlaylists: TextView
    private var lastSearchResults: List<MusicTrack> = emptyList()
    private var searchFilter: String = "all"

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
    private lateinit var switchSkipSilence: Switch
    private lateinit var switchCrossfade: Switch
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
    private var lyricsJob: Job? = null
    private var lyricsResult: LyricsResult? = null
    private val lyricsHandler = Handler(Looper.getMainLooper())
    private var pendingTrack: MusicTrack? = null
    private val searchHistory = mutableListOf<String>()
    private val searchPrefs by lazy { getSharedPreferences("reo_search_history", MODE_PRIVATE) }

    private val uiHandler = Handler(Looper.getMainLooper())
    private var isDraggingProgress = false

    /** Player doesn't expose an audioSessionId getter — only the listener callback — so we cache it. */
    private var currentAudioSessionId: Int = 0

    /** videoId -> last resolved stream URL, so Download doesn't need a fresh network call. */
    private val resolvedStreamUrls = mutableMapOf<String, String>()

    // Feeds PlaybackSignalStore: last-known position/duration/track before a transition fires.
    private var lastKnownPositionMs: Long = 0L
    private var lastKnownDurationMs: Long = 0L
    private var lastKnownTrack: MusicTrack? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
            val newTrack = mediaItem?.let {
                MusicTrack(
                    videoId = it.mediaId,
                    title = it.mediaMetadata.title?.toString() ?: "",
                    artist = it.mediaMetadata.artist?.toString() ?: "",
                    album = it.mediaMetadata.albumTitle?.toString() ?: "",
                    thumbnailUrl = it.mediaMetadata.artworkUri?.toString() ?: ""
                )
            }
            if (newTrack != null) {
                PlaybackSignalStore.recordTransition(lastKnownTrack, lastKnownPositionMs, lastKnownDurationMs, newTrack)
                lastKnownTrack = newTrack
                lastKnownPositionMs = 0L
                lastKnownDurationMs = 0L
            }
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        AppSettings.init(this)
        applyThemeMode(AppSettings.themeMode)

        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) DynamicColors.applyToActivityIfAvailable(this)

        PlayHistoryStore.init(this)
        PlaybackSignalStore.init(this)
        FavoritesStore.init(this)
        PlaylistStore.init(this)
        DownloadStore.init(this)
        OfflineCacheManager.init(this)

        setContentView(R.layout.activity_main)

        bindViews()
        (screenHome as? android.widget.ScrollView)?.isVerticalScrollBarEnabled = false
        (screenSearch as? android.widget.ScrollView)?.isVerticalScrollBarEnabled = false
        (screenLibrary as? android.widget.ScrollView)?.isVerticalScrollBarEnabled = false
        wireBottomNav()
        wireHomeShortcuts()
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
        installTabSwipeGestures()

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
        npLyricsBtn = findViewById(R.id.np_lyrics_btn)

        lyricsOverlay = findViewById(R.id.lyrics_overlay)
        lyricsBack = findViewById(R.id.lyrics_back)
        lyricsTitle = findViewById(R.id.lyrics_title)
        lyricsArtist = findViewById(R.id.lyrics_artist)
        lyricsText = findViewById(R.id.lyrics_text)
        lyricsScroll = findViewById(R.id.lyrics_scroll)
        npQueueHeader = findViewById(R.id.np_queue_header)
        npQueueContainer = findViewById(R.id.np_queue_container)
        npQueueEmpty = findViewById(R.id.np_queue_empty)

        homeGreeting = findViewById(R.id.home_greeting)
        homeRecentSection = findViewById(R.id.home_recent_section)
        homeRecentContainer = findViewById(R.id.home_recent_container)
        homePicksSection = findViewById(R.id.home_picks_section)
        homePicksHeader = findViewById(R.id.home_picks_header)
        homePicksContainer = findViewById(R.id.home_picks_container)
        homeDiscoverContainer = findViewById(R.id.home_discover_container)
        homeEmptyState = findViewById(R.id.home_empty_state)
        homeHero = findViewById(R.id.home_hero)
        homeHeroArt = findViewById(R.id.home_hero_art)
        homeHeroTitle = findViewById(R.id.home_hero_title)
        homeHeroArtist = findViewById(R.id.home_hero_artist)
        homeHeroPlay = findViewById(R.id.home_hero_play)
        homeSearchShortcut = findViewById(R.id.home_search_shortcut)
        homeSettingsShortcut = findViewById(R.id.home_settings_shortcut)
        homeChipMadeForYou = findViewById(R.id.home_chip_made_for_you)
        homeChipDiscover = findViewById(R.id.home_chip_discover)
        homeChipMoods = findViewById(R.id.home_chip_moods)
        homeChipCharts = findViewById(R.id.home_chip_charts)

        searchInput = findViewById(R.id.search_input)
        searchButton = findViewById(R.id.search_button)
        searchClearButton = findViewById(R.id.search_clear_button)
        searchStatus = findViewById(R.id.search_status)
        resultsContainer = findViewById(R.id.results_container)
        searchRecentSection = findViewById(R.id.search_recent_section)
        searchRecentContainer = findViewById(R.id.search_recent_container)
        searchClearHistory = findViewById(R.id.search_clear_history)
        searchFilterAll = findViewById(R.id.search_filter_all)
        searchFilterSongs = findViewById(R.id.search_filter_songs)
        searchFilterArtists = findViewById(R.id.search_filter_artists)
        searchFilterAlbums = findViewById(R.id.search_filter_albums)
        searchFilterPlaylists = findViewById(R.id.search_filter_playlists)

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
        switchSkipSilence = findViewById(R.id.switch_skip_silence)
        switchCrossfade = findViewById(R.id.switch_crossfade)
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

    private fun wireHomeShortcuts() {
        homeSearchShortcut.setOnClickListener { showTab(Tab.SEARCH); searchInput.requestFocus(); showKeyboard(searchInput) }
        homeSettingsShortcut.setOnClickListener { showTab(Tab.SETTINGS) }
        homeHeroPlay.setOnClickListener { PlayHistoryStore.getRecent().firstOrNull()?.let { playTrack(it) } }
        homeChipMadeForYou.setOnClickListener { loadHomeContent() }
        homeChipDiscover.setOnClickListener { showTab(Tab.SEARCH); searchInput.setText("trending music"); search("trending music") }
        homeChipMoods.setOnClickListener { showTab(Tab.SEARCH); searchInput.setText("chill mood music"); search("chill mood music") }
        homeChipCharts.setOnClickListener { showTab(Tab.SEARCH); searchInput.setText("top songs"); search("top songs") }
    }

    private fun showKeyboard(view: View) {
        view.post { val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java); imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
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
        tabHome.background = if (tab == Tab.HOME) ContextCompat.getDrawable(this, R.drawable.bg_nav_selected) else null
        tabSearch.background = if (tab == Tab.SEARCH) ContextCompat.getDrawable(this, R.drawable.bg_nav_selected) else null
        tabLibrary.background = if (tab == Tab.LIBRARY) ContextCompat.getDrawable(this, R.drawable.bg_nav_selected) else null
        tabSettings.background = if (tab == Tab.SETTINGS) ContextCompat.getDrawable(this, R.drawable.bg_nav_selected) else null

        if (tab == Tab.HOME) loadHomeContent()
        if (tab == Tab.SEARCH) renderSearchHistory()
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
        npLyricsBtn.setOnClickListener { openLyrics() }
        lyricsBack.setOnClickListener { closeLyrics() }

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

        // YouTube Music-style gestures on the album-art surface:
        // tap = play/pause, double-tap = like, long-press = track actions,
        // horizontal swipe = next/previous, vertical swipe = lyrics/close.
        val artGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                togglePlayPause()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val track = currentPlayingTrack() ?: return true
                val liked = FavoritesStore.toggle(track)
                refreshHeartIcon(liked)
                Toast.makeText(this@MainActivity, if (liked) "Added to liked songs" else "Removed from liked songs", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                currentPlayingTrack()?.let { showTrackOptionsMenu(it) }
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val diffX = e2.x - start.x
                val diffY = e2.y - start.y

                if (abs(diffX) > 120f && abs(velocityX) > 300f && abs(diffX) > abs(diffY)) {
                    if (diffX < 0f) mediaController?.seekToNextMediaItem()
                    else mediaController?.seekToPreviousMediaItem()
                    return true
                }

                if (abs(diffY) > 150f && abs(velocityY) > 300f && abs(diffY) > abs(diffX)) {
                    if (diffY < 0f) openLyrics() else closeNowPlaying()
                    return true
                }

                return false
            }
        })
        npArtGestureArea.setOnTouchListener { _, event -> artGestureDetector.onTouchEvent(event) }

        // Long-press the queue header to jump straight to the full queue.
        npQueueHeader.setOnLongClickListener {
            openListScreen(ListMode.QUEUE)
            true
        }
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

        refreshHeartIcon(currentPlayingTrack()?.let { FavoritesStore.isLiked(it.videoId) } ?: false)
        refreshDownloadIcon()

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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            lyricsOverlay.visibility == View.VISIBLE -> closeLyrics()
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

        val miniGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                openNowPlaying()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                togglePlayPause()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                currentPlayingTrack()?.let { showTrackOptionsMenu(it) }
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val diffX = e2.x - start.x
                val diffY = e2.y - start.y
                if (abs(diffX) > 90f && abs(velocityX) > 250f && abs(diffX) > abs(diffY)) {
                    if (diffX < 0f) mediaController?.seekToNextMediaItem()
                    else mediaController?.seekToPreviousMediaItem()
                    return true
                }
                if (abs(diffY) > 90f && abs(velocityY) > 250f && abs(diffY) > abs(diffX)) {
                    if (diffY < 0f) openNowPlaying() else closeNowPlaying()
                    return true
                }
                return false
            }
        })
        miniPlayer.setOnTouchListener { _, event -> miniGestureDetector.onTouchEvent(event) }
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

    private val homeDiscoverSeeds = listOf(
        "trending songs" to "Trending Now",
        "top hits" to "Popular Right Now",
        "chill lofi beats" to "Chill Vibes",
        "workout motivation songs" to "Workout Mix",
        "bollywood hit songs" to "Bollywood Hits",
        "romantic love songs" to "Love Songs",
        "top rock songs" to "Rock Classics",
        "throwback 2000s hits" to "Throwback Hits"
    )

    private fun loadHomeContent() {

        homeLoadJob?.cancel()

        homeGreeting.text = timeOfDayGreeting()

        val recent = PlayHistoryStore.getRecent()
        homeHero.visibility = if (recent.isNotEmpty()) View.VISIBLE else View.GONE
        recent.firstOrNull()?.let { track ->
            homeHeroTitle.text = track.title
            homeHeroArtist.text = track.artist.ifBlank { "Unknown artist" }
            loadArtwork(homeHeroArt, track)
            homeHeroPlay.setOnClickListener { playTrack(track) }
        }
        homeRecentContainer.removeAllViews()
        homePicksContainer.removeAllViews()
        homeDiscoverContainer.removeAllViews()
        homeEmptyState.visibility = View.GONE

        if (recent.isEmpty()) {
            homeRecentSection.visibility = View.GONE
            homePicksSection.visibility = View.GONE
        } else {
            homeRecentSection.visibility = View.VISIBLE
            homePicksSection.visibility = View.VISIBLE
            homePicksHeader.text = "Made for you"
            recent.take(10).forEach { track -> addRecentCard(track) }
        }

        homeLoadJob = screenScope.launch {

            if (recent.isNotEmpty()) {
                val seedId = recent.first().videoId
                val resolution = withContext(Dispatchers.IO) { musicProvider.resolveTrack(seedId) }
                val ranked = PlaybackSignalStore.rankByAffinity(seedId, resolution?.relatedTracks.orEmpty())
                ranked.take(10).forEach { track ->
                    addHomePickCard(homePicksContainer, track)
                }
            }

            // Personalized rows from whichever artists this device actually
            // finishes listening to most (not just "last played").
            val topArtists = PlaybackSignalStore.topArtists(2)
            topArtists.forEach { artist ->
                val tracks = withContext(Dispatchers.IO) { musicProvider.search(artist) }.take(10)
                if (tracks.isNotEmpty()) {
                    addDiscoverSection("More $artist", tracks)
                }
            }

            // Always-on Spotify-style discovery rows, so the app never opens to a blank page.
            var anyDiscoverLoaded = false
            homeDiscoverSeeds.shuffled().take(4).forEach { (query, label) ->
                val tracks = withContext(Dispatchers.IO) { musicProvider.search(query) }.take(10)
                if (tracks.isNotEmpty()) {
                    anyDiscoverLoaded = true
                    addDiscoverSection(label, tracks)
                }
            }

            if (!anyDiscoverLoaded && recent.isEmpty()) {
                homeEmptyState.visibility = View.VISIBLE
            }
        }
    }

    private fun timeOfDayGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 5 -> "Late night listening."
            hour < 12 -> "Good morning."
            hour < 17 -> "Good afternoon."
            hour < 21 -> "Good evening."
            else -> "Good night."
        }
    }

    private fun addHomePickCard(container: LinearLayout, track: MusicTrack) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_home_pick, container, false)
        val art = card.findViewById<ImageView>(R.id.pick_art)
        val title = card.findViewById<TextView>(R.id.pick_title)
        val artist = card.findViewById<TextView>(R.id.pick_artist)
        val more = card.findViewById<ImageButton>(R.id.pick_more)
        title.text = track.title
        artist.text = track.artist.ifBlank { "Unknown artist" }
        loadArtwork(art, track)
        card.setOnClickListener { playTrack(track) }
        card.setOnLongClickListener { showTrackOptionsMenu(track); true }
        more.setOnClickListener { showTrackOptionsMenu(track) }
        container.addView(card)
    }

    private fun addDiscoverSection(title: String, tracks: List<MusicTrack>) {
        val section = LayoutInflater.from(this).inflate(R.layout.item_home_section, homeDiscoverContainer, false)
        section.findViewById<TextView>(R.id.section_title).text = title
        val row = section.findViewById<LinearLayout>(R.id.section_row_container)

        tracks.forEach { track ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_recent_card, row, false)
            val art = card.findViewById<ImageView>(R.id.recent_art)
            val cardTitle = card.findViewById<TextView>(R.id.recent_title)
            val cardArtist = card.findViewById<TextView>(R.id.recent_artist)

            cardTitle.text = track.title
            cardArtist.text = track.artist.ifBlank { "Unknown artist" }

            loadArtwork(art, track)

            card.setOnClickListener { playTrack(track) }
            card.setOnLongClickListener { showTrackOptionsMenu(track); true }
            row.addView(card)
        }

        homeDiscoverContainer.addView(section)
    }

    private fun addRecentCard(track: MusicTrack) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_recent_card, homeRecentContainer, false)
        val art = card.findViewById<ImageView>(R.id.recent_art)
        val title = card.findViewById<TextView>(R.id.recent_title)
        val artist = card.findViewById<TextView>(R.id.recent_artist)

        title.text = track.title
        artist.text = track.artist.ifBlank { "Unknown artist" }

        loadArtwork(art, track)

        card.setOnClickListener { playTrack(track) }
        card.setOnLongClickListener { showTrackOptionsMenu(track); true }
        homeRecentContainer.addView(card)
    }

    private fun loadArtwork(view: ImageView, track: MusicTrack) {
        val fallback = if (track.videoId.isNotBlank()) "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg" else ""
        val url = track.thumbnailUrl.ifBlank { fallback }
        view.setImageResource(R.drawable.bg_thumb_small)
        if (url.isNotBlank()) {
            view.load(url) {
                placeholder(R.drawable.bg_thumb_small)
                error(R.drawable.bg_thumb_small)
                crossfade(180)
                listener(onError = { _, _ ->
                    if (url != fallback && fallback.isNotBlank()) {
                        view.load(fallback) { placeholder(R.drawable.bg_thumb_small); error(R.drawable.bg_thumb_small) }
                    }
                })
            }
        }
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    private fun wireSearchScreen() {
        loadSearchHistory()
        searchButton.setOnClickListener { search(searchInput.text.toString()) }
        searchInput.setOnEditorActionListener { _, _, _ -> search(searchInput.text.toString()); true }
        searchClearButton.setOnClickListener {
            searchInput.text.clear()
            resultsContainer.removeAllViews()
            searchStatus.visibility = View.GONE
            searchClearButton.visibility = View.GONE
            renderSearchHistory()
        }
        searchClearHistory.setOnClickListener {
            searchHistory.clear()
            saveSearchHistory()
            renderSearchHistory()
        }
        searchFilterAll.setOnClickListener { setSearchFilter("all") }
        searchFilterSongs.setOnClickListener { setSearchFilter("songs") }
        searchFilterArtists.setOnClickListener { setSearchFilter("artists") }
        searchFilterAlbums.setOnClickListener { setSearchFilter("albums") }
        searchFilterPlaylists.setOnClickListener { setSearchFilter("playlists") }
    }

    private fun loadSearchHistory() {
        searchHistory.clear()
        searchHistory.addAll(searchPrefs.getStringSet("queries", emptySet()).orEmpty().take(8))
    }

    private fun saveSearchHistory() {
        searchPrefs.edit().putStringSet("queries", searchHistory.toSet()).apply()
    }

    private fun rememberSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        searchHistory.removeAll { it.equals(q, ignoreCase = true) }
        searchHistory.add(0, q)
        while (searchHistory.size > 8) searchHistory.removeAt(searchHistory.lastIndex)
        saveSearchHistory()
    }

    private fun renderSearchHistory() {
        searchRecentContainer.removeAllViews()
        searchRecentSection.visibility = if (searchHistory.isEmpty()) View.GONE else View.VISIBLE
        if (searchHistory.isEmpty()) return
        searchHistory.forEach { query ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(8, 8, 8, 8)
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card_rounded_pressed)
                isClickable = true
                isFocusable = true
            }
            val icon = ImageView(this).apply {
                setImageResource(R.drawable.ic_history)
                setColorFilter(colorOf(R.color.text_muted))
                layoutParams = LinearLayout.LayoutParams(20, 20)
            }
            val text = TextView(this).apply {
                this.text = query
                setTextColor(colorOf(R.color.text_primary))
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12 }
            }
            val more = ImageButton(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(colorOf(R.color.text_faint))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.ripple_circle_ghost)
                setPadding(10, 10, 10, 10)
                layoutParams = LinearLayout.LayoutParams(40, 40)
                contentDescription = "Remove search"
                setOnClickListener {
                    searchHistory.remove(query)
                    saveSearchHistory()
                    renderSearchHistory()
                }
            }
            row.addView(icon); row.addView(text); row.addView(more)
            row.setOnClickListener { searchInput.setText(query); searchInput.setSelection(query.length); search(query) }
            searchRecentContainer.addView(row, LinearLayout.LayoutParams(-1, 50).apply { bottomMargin = 5 })
        }
    }

    private fun setSearchFilter(filter: String) {
        searchFilter = filter
        val views = listOf(searchFilterAll, searchFilterSongs, searchFilterArtists, searchFilterAlbums, searchFilterPlaylists)
        val keys = listOf("all", "songs", "artists", "albums", "playlists")
        views.forEachIndexed { i, v -> v.background = ContextCompat.getDrawable(this, if (keys[i] == filter) R.drawable.bg_chip_selected else R.drawable.bg_chip) }
        if (lastSearchResults.isNotEmpty()) renderFilteredSearchResults()
    }

    private fun search(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        rememberSearch(clean)
        searchClearButton.visibility = View.VISIBLE
        resultsContainer.removeAllViews()
        searchRecentSection.visibility = View.GONE
        searchStatus.visibility = View.VISIBLE
        searchStatus.text = "Searching REO…"
        screenScope.launch {
            val results = withContext(Dispatchers.IO) { musicProvider.search(clean).take(50) }
            lastSearchResults = results
            if (results.isEmpty()) {
                searchStatus.text = "No results for \"$clean\""
                return@launch
            }
            searchStatus.visibility = View.GONE
            renderFilteredSearchResults()
        }
    }

    private fun renderFilteredSearchResults() {
        resultsContainer.removeAllViews()
        val results = lastSearchResults
        when (searchFilter) {
            "artists" -> {
                val artists = results.map { it.artist.ifBlank { "Unknown artist" } }.distinct().take(20)
                addSearchSectionHeader("Artists")
                artists.forEach { artist -> addSearchEntityRow(artist, "Artist", R.drawable.ic_library) { search(artist) } }
            }
            "albums" -> {
                val albums = results.map { it.album }.filter { it.isNotBlank() }.distinct().take(20)
                if (albums.isEmpty()) {
                    addSearchSectionHeader("Albums")
                    addSearchEmptyRow("Album information is not available for these results yet.")
                } else {
                    addSearchSectionHeader("Albums")
                    albums.forEach { album ->
                        val track = results.first { it.album == album }
                        addSearchEntityRow(album, track.artist, R.drawable.ic_playlist) { playTrack(track) }
                    }
                }
            }
            "playlists" -> {
                addSearchSectionHeader("Your playlists")
                val playlists = PlaylistStore.getAll().filter { it.name.contains(searchInput.text.toString(), true) }
                if (playlists.isEmpty()) addSearchEmptyRow("No matching playlists")
                playlists.forEach { playlist -> addSearchEntityRow(playlist.name, "Playlist", R.drawable.ic_playlist) { openListScreen(ListMode.PLAYLIST, playlist.id) } }
            }
            else -> {
                addSearchSectionHeader("Songs")
                results.forEach { track -> addTrackRow(resultsContainer, track) }
            }
        }
    }

    private fun addSearchSectionHeader(title: String) {
        val header = TextView(this).apply {
            text = title
            setTextColor(colorOf(R.color.text_primary))
            textSize = 18f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(0, 8, 0, 10)
        }
        resultsContainer.addView(header)
    }

    private fun addSearchEmptyRow(message: String) {
        val text = TextView(this).apply {
            this.text = message
            setTextColor(colorOf(R.color.text_muted))
            textSize = 13f
            setPadding(0, 8, 0, 20)
        }
        resultsContainer.addView(text)
    }

    private fun addSearchEntityRow(titleValue: String, subtitleValue: String, iconRes: Int, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card_rounded_pressed)
            setPadding(12, 10, 12, 10)
            isClickable = true
            isFocusable = true
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(colorOf(R.color.accent))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_recent_card)
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(48, 48)
        }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 12 } }
        val title = TextView(this).apply { this.text = titleValue; setTextColor(colorOf(R.color.text_primary)); textSize = 14f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val sub = TextView(this).apply { this.text = subtitleValue; setTextColor(colorOf(R.color.text_muted)); textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        text.addView(title); text.addView(sub)
        row.addView(icon); row.addView(text)
        row.setOnClickListener { action() }
        row.setOnLongClickListener { action(); true }
        resultsContainer.addView(row, LinearLayout.LayoutParams(-1, 68).apply { bottomMargin = 6 })
    }

    /** Generic track row used by Home picks, Search results, and various list screens. */
    private fun addTrackRow(
        container: LinearLayout,
        track: MusicTrack,
        onRemove: (() -> Unit)? = null,
        removeLabel: String = "Remove"
    ) {
        val item = LayoutInflater.from(this).inflate(R.layout.item_search_result, container, false)

        val art = item.findViewById<ImageView>(R.id.item_art)
        val title = item.findViewById<TextView>(R.id.item_title)
        val artist = item.findViewById<TextView>(R.id.item_artist)
        val more = item.findViewById<ImageButton>(R.id.item_more)

        title.text = track.title
        artist.text = track.artist.ifBlank { "Unknown artist" }

        loadArtwork(art, track)

        val onPlay = View.OnClickListener { playTrack(track) }
        item.setOnClickListener(onPlay)
        item.setOnLongClickListener { showTrackOptionsMenu(track, onRemove, removeLabel); true }
        more.setOnClickListener { showTrackOptionsMenu(track, onRemove, removeLabel) }

        container.addView(item)
    }

    /**
     * Single overflow menu used everywhere instead of a row full of icon
     * buttons: like, add to playlist, add to queue, download, and an
     * optional context-specific remove action (e.g. "Remove from playlist").
     */
    private fun showTrackOptionsMenu(
        track: MusicTrack,
        onRemove: (() -> Unit)? = null,
        removeLabel: String = "Remove"
    ) {
        val liked = FavoritesStore.isLiked(track.videoId)
        val downloaded = DownloadStore.isDownloaded(track.videoId)

        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        labels.add(if (liked) "Remove from Liked Songs" else "Add to Liked Songs")
        actions.add { FavoritesStore.toggle(track) }

        labels.add("Add to playlist")
        actions.add { showAddToPlaylistDialog(track) }

        labels.add("Play next")
        actions.add { playTrackNext(track) }

        labels.add("Add to queue")
        actions.add { addTrackToQueue(track) }

        if (downloaded) {
            labels.add("Downloaded \u2713")
            actions.add { Toast.makeText(this, "Already downloaded", Toast.LENGTH_SHORT).show() }
        } else {
            labels.add("Download")
            actions.add { downloadTrack(track) {} }
        }

        if (onRemove != null) {
            labels.add(removeLabel)
            actions.add(onRemove)
        }

        AlertDialog.Builder(this)
            .setTitle(track.title)
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun playTrackNext(track: MusicTrack) {
        val controller = mediaController ?: return
        screenScope.launch {
            val streamUrl = withContext(Dispatchers.IO) { musicProvider.getStreamUrl(track.videoId) }
            if (streamUrl.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, "Couldn't prepare track", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val insertAt = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertAt, buildMediaItem(track, streamUrl))
            refreshQueueList()
            Toast.makeText(this@MainActivity, "Playing next", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addTrackToQueue(track: MusicTrack) {
        val controller = mediaController ?: return
        screenScope.launch {
            val streamUrl = withContext(Dispatchers.IO) { musicProvider.getStreamUrl(track.videoId) }
            if (streamUrl.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, "Couldn't add to queue", Toast.LENGTH_SHORT).show()
                return@launch
            }
            controller.addMediaItem(buildMediaItem(track, streamUrl))
            refreshQueueList()
            Toast.makeText(this@MainActivity, "Added to queue", Toast.LENGTH_SHORT).show()
        }
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
            val removeLabel = when (mode) {
                ListMode.FAVORITES -> "Remove from Liked Songs"
                ListMode.DOWNLOADS -> "Remove download"
                ListMode.PLAYLIST -> "Remove from playlist"
                ListMode.HISTORY -> "Remove from history"
                ListMode.QUEUE -> "Remove"
            }
            addTrackRow(
                listContainer, track,
                removeLabel = removeLabel,
                onRemove = {
                    when (mode) {
                        ListMode.FAVORITES -> FavoritesStore.remove(track.videoId)
                        ListMode.DOWNLOADS -> {
                            DownloadStore.get(track.videoId)?.let { OfflineCacheManager.removeFromCache(it.streamUrl) }
                            DownloadStore.remove(track.videoId)
                        }
                        ListMode.PLAYLIST -> currentPlaylistId?.let { PlaylistStore.removeTrack(it, track.videoId) }
                        ListMode.HISTORY -> PlayHistoryStore.remove(track.videoId)
                        else -> {}
                    }
                    renderCurrentList()
                    if (currentTab == Tab.LIBRARY) loadLibraryContent()
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
                for (candidate in rest.take(50)) {
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
                    .setArtworkUri(Uri.parse(track.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg" }))
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
            for (i in (currentIndex + 1) until minOf(count, currentIndex + 1 + 15)) {
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
        row.setOnLongClickListener {
            val track = MusicTrack(
                videoId = item.mediaId,
                title = item.mediaMetadata.title?.toString() ?: "Unknown title",
                artist = item.mediaMetadata.artist?.toString() ?: "",
                album = item.mediaMetadata.albumTitle?.toString() ?: "",
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString() ?: ""
            )
            showTrackOptionsMenu(track)
            true
        }
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

    private fun installTabSwipeGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || abs(velocityX) < 500f || abs(velocityX) < abs(velocityY) * 1.15f) return false
                val tabs = Tab.values()
                val index = tabs.indexOf(currentTab)
                val next = if (velocityX < 0) index + 1 else index - 1
                if (next in tabs.indices) showTab(tabs[next])
                return true
            }
        })
        listOf(screenHome, screenSearch, screenLibrary, screenSettings).forEach { screen ->
            screen.setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
        }
    }

    // ---------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------

    private fun sendPlaybackSettingUpdate() {
        mediaController?.let { controller ->
            val command = SessionCommand("REO_SET_SKIP_SILENCE", android.os.Bundle.EMPTY)
            val args = android.os.Bundle().apply { putBoolean("enabled", AppSettings.skipSilenceEnabled) }
            controller.sendCustomCommand(command, args)
        }
    }

    private fun wireSettingsScreen() {

        switchDataSaver.isChecked = AppSettings.dataSaverEnabled
        switchKeepScreenOn.isChecked = AppSettings.keepScreenOnEnabled
        switchSmartShuffle.isChecked = AppSettings.smartShuffleEnabled
        switchSkipSilence.isChecked = AppSettings.skipSilenceEnabled
        switchCrossfade.isChecked = AppSettings.crossfadeEnabled

        switchDataSaver.setOnCheckedChangeListener { _, isChecked -> AppSettings.setDataSaverEnabled(isChecked) }
        switchSkipSilence.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setSkipSilenceEnabled(isChecked)
            sendPlaybackSettingUpdate()
        }
        switchCrossfade.setOnCheckedChangeListener { _, isChecked -> AppSettings.setCrossfadeEnabled(isChecked) }
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
        val artwork = artworkUri?.toString().orEmpty().ifBlank {
            if (item.mediaId.isNotBlank()) "https://i.ytimg.com/vi/${item.mediaId}/hqdefault.jpg" else ""
        }
        npAlbumArt.setImageResource(R.drawable.bg_album_art_placeholder)
        miniArt.setImageResource(R.drawable.bg_thumb_small)
        if (artwork.isNotBlank()) {
            val fallback = if (item.mediaId.isNotBlank()) "https://i.ytimg.com/vi/${item.mediaId}/hqdefault.jpg" else ""
            npAlbumArt.load(artwork) {
                placeholder(R.drawable.bg_album_art_placeholder)
                error(R.drawable.bg_album_art_placeholder)
                crossfade(180)
                listener(onError = { _, _ ->
                    if (fallback.isNotBlank() && artwork != fallback) {
                        npAlbumArt.load(fallback) { placeholder(R.drawable.bg_album_art_placeholder); error(R.drawable.bg_album_art_placeholder); crossfade(120) }
                    }
                })
            }
            miniArt.load(artwork) {
                placeholder(R.drawable.bg_thumb_small)
                error(R.drawable.bg_thumb_small)
                crossfade(180)
                listener(onError = { _, _ ->
                    if (fallback.isNotBlank() && artwork != fallback) {
                        miniArt.load(fallback) { placeholder(R.drawable.bg_thumb_small); error(R.drawable.bg_thumb_small); crossfade(120) }
                    }
                })
            }
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

            // Track the last-known position/duration of the current item so
            // PlaybackSignalStore can tell a completed listen from a skip
            // once the item actually changes (see onMediaItemTransition).
            lastKnownPositionMs = position
            lastKnownDurationMs = duration
        }
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "0:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun openLyrics() {
        val track = currentPlayingTrack() ?: return
        lyricsOverlay.visibility = View.VISIBLE
        lyricsTitle.text = track.title
        lyricsArtist.text = track.artist.ifBlank { "Unknown artist" }
        lyricsText.text = "Finding lyrics…"
        lyricsText.setTextColor(colorOf(R.color.text_muted))
        lyricsScroll.scrollTo(0, 0)
        lyricsJob?.cancel()
        lyricsJob = screenScope.launch {
            val result = withContext(Dispatchers.IO) { LyricsRepository.getLyrics(track.title, track.artist) }
            lyricsResult = result
            if (result == null) {
                lyricsText.text = "Lyrics aren't available for this track."
                return@launch
            }
            renderLyrics(mediaController?.currentPosition ?: 0L)
            lyricsHandler.removeCallbacks(lyricsTicker)
            lyricsHandler.post(lyricsTicker)
        }
    }

    private fun closeLyrics() {
        lyricsHandler.removeCallbacks(lyricsTicker)
        lyricsJob?.cancel()
        lyricsOverlay.visibility = View.GONE
    }

    private val lyricsTicker = object : Runnable {
        override fun run() {
            if (lyricsOverlay.visibility == View.VISIBLE) {
                renderLyrics(mediaController?.currentPosition ?: 0L)
                lyricsHandler.postDelayed(this, 180L)
            }
        }
    }

    private fun renderLyrics(positionMs: Long) {
        val result = lyricsResult ?: return
        if (result.lines.isEmpty()) {
            lyricsText.text = result.plainText
            return
        }
        val builder = SpannableStringBuilder()
        val accent = colorOf(R.color.accent)
        val muted = colorOf(R.color.text_muted)
        val primary = colorOf(R.color.text_primary)
        var activeLineIndex = -1
        result.lines.forEachIndexed { index, line ->
            if (positionMs >= line.startMs && positionMs < line.endMs) activeLineIndex = index
            val start = builder.length
            builder.append(line.text)
            builder.append("\n\n")
            val lineEnd = start + line.text.length
            builder.setSpan(ForegroundColorSpan(if (index == activeLineIndex) accent else muted), start, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (index == activeLineIndex) {
                val words = line.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.isNotEmpty()) {
                    val elapsed = (positionMs - line.startMs).coerceAtLeast(0L)
                    val lineDuration = (line.endMs - line.startMs).coerceAtLeast(500L)
                    val wordIndex = ((elapsed.toDouble() / lineDuration.toDouble()) * words.size).toInt().coerceIn(0, words.lastIndex)
                    var cursor = start
                    words.forEachIndexed { wi, word ->
                        val idx = builder.indexOf(word, cursor)
                        if (idx >= 0) {
                            val end = idx + word.length
                            builder.setSpan(ForegroundColorSpan(if (wi <= wordIndex) primary else accent), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            cursor = end
                        }
                    }
                }
            }
        }
        lyricsText.text = builder
        if (activeLineIndex >= 0) {
            val layout = lyricsText.layout
            if (layout != null) {
                val lineStart = layout.getLineTop(activeLineIndex.coerceAtMost(layout.lineCount - 1))
                val target = (lineStart - lyricsScroll.height / 3).coerceAtLeast(0)
                lyricsScroll.smoothScrollTo(0, target)
            }
        }
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
        lyricsHandler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(tickRunnable)
        mediaController?.removeListener(playerListener)
        FavoritesStore.removeListener(::onFavoritesChanged)
        PlaylistStore.removeListener(::onPlaylistsChanged)
        EqualizerManager.release()
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
