# REO Music 3.0.0 - Ultimate local listener build

This release keeps REO as a **single-device, single-listener** music client. There are no accounts, login flows, cloud profiles, Firebase/Supabase auth, or remote recommendation databases.

## Playback
- Media3 background playback and MediaSession
- Persistent local queue and resume position
- Gapless playback through Media3 playlist handling
- Dual-player true crossfade engine
- Configurable crossfade duration
- Skip silence
- Playback speed
- Repeat and shuffle
- Sleep timer
- Equalizer integration
- Bluetooth/headset/media-button controls
- Notification and lock-screen controls

## Discovery
- Local listening-signal ranking
- Continue Listening
- Made for you / Quick Picks
- Related-track discovery
- Artist-driven discovery rows
- Search history
- Debounced search
- Request cancellation
- Voice search via Activity Result API

## Player UX
- Large album artwork with thumbnail fallback
- Album context metadata
- Dynamic artwork-aware player foundations
- Swipe next/previous
- Swipe-up lyrics
- Swipe-down dismissal
- Long-press actions
- Double-tap like/play actions
- Large Up Next preview and full queue access
- Queue drag/reorder and swipe removal

## Offline / local
- Downloads and download-only library
- Local playback cache
- Cache size and cleanup controls
- Local likes, playlists, history and recommendations
- No cloud account requirement

## UI/UX
- Material-based UI
- System/light/dark theme support
- Dynamic color support where available
- Reduced-motion/animation/haptics feature flags
- Responsive low-end-friendly lists and artwork loading
- Explicit loading, empty, error and offline states

## Android modernization
- `OnBackPressedDispatcher` instead of deprecated `onBackPressed()`
- Activity Result API instead of deprecated `startActivityForResult()`
- Java/Kotlin 17 toolchain
- GitHub Actions build for debug/release APKs and source archive

## Big UX update additions
- Android home-screen Now Playing widget
- Modern Android back navigation and voice-search APIs
- Configurable haptic feedback
- Reduced-motion mode
- Dynamic-color toggle
- Persistent-queue toggle
- Startup queue-resume toggle
- Automatic-cache-cleanup preference
- Real cache clearing instead of a settings-only toast
