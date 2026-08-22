# REO Music

A lightweight YouTube Music-style Android music client built around Media3, NewPipe Extractor and Coil.

## What's in this revision

- Media3 background playback through `MediaSessionService`
- Gapless playlist transitions supported by Media3's queue engine
- Skip-silence playback setting
- Smart disk caching and explicit offline downloads
- Lock-screen / notification media controls through MediaSession
- Swipe-down now-playing sheet and swipe-left/right track gestures
- LRCLIB synced lyrics with animated line + estimated word highlighting
- Material 3 dynamic colors on Android 12+
- Cleaner YouTube Music-inspired home feed
- Local personalized recommendations using listening history and related tracks
- Robust YouTube artwork fallback using `i.ytimg.com`
- Release build configuration and GitHub Actions APK builds

## Recommendation model

REO's current recommendations are local-first. It uses recent listening history, completion signals, artist affinity and related tracks instead of sending a user's listening history to an AI service. This keeps the app lightweight and private while still producing personalized rows.

## Important playback note

Media3 provides gapless playlist playback and skip-silence. A true overlapping two-player crossfade requires a dedicated audio mixer/dual-player layer. The current optional "Smooth transitions" setting uses a short transition fade-in rather than falsely claiming a true crossfade. That layer should be replaced by a dedicated mixer before a commercial production launch.

## Build

The repository includes a GitHub Actions workflow that builds `:app:assembleDebug` on Ubuntu with Java 17 and Gradle 8.13.

## REO Music 2.0.0 — Big UI/Playback Upgrade

- YouTube Music-inspired full-screen Now Playing experience.
- Fixed album-art card sizing so artwork has a real square layout instead of collapsing to zero height.
- Removed the old waveform visualizer and its permission; this also removes the persistent `-------` artifact.
- Up Next now previews up to 12 tracks instead of 5.
- Direct playlists/search queues can stage up to 50 upcoming tracks.
- Smart Shuffle resolves up to 12 related tracks per refill.
- Larger, cleaner queue rows and a dedicated View all queue action.
- Material/dynamic theme architecture and Media3 playback remain intact.
