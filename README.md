# REO Music 3.0.0

REO Music 3.0.0 is a local-first, single-device music client built around Media3, NewPipe Extractor and Coil. It keeps one listener's playback, recommendations, likes, playlists, history, downloads and cache on the device. There are no accounts, cloud profiles or REO user databases.

## 3.0.0 upgrade

- Premium YouTube Music-inspired Now Playing sheet with a real album-art card, clean metadata hierarchy and a numbered Up Next section.
- Removed the obsolete decorative player line/handle artifact completely.
- Up Next preview expanded to 20 tracks; smart queue look-ahead expanded to 30 candidates.
- Large queue support remains lazy and editable through the full queue screen.
- Search now has local history, debounced queries and voice-search entry.
- Home discovery uses additional local-affinity and provider discovery rows.
- Existing Media3 background playback, gapless queue playback, skip-silence, optional dual-player crossfade, downloads, cache, lyrics, equalizer and local recommendation signals are preserved.
- Artwork loading keeps provider artwork first and falls back to the YouTube thumbnail endpoint when necessary.
- Gesture interactions include swipe navigation, long-press track actions, double-tap like/play controls and haptic feedback.
- Material 3 dynamic theming, local settings and single-listener privacy remain the foundation.

## Recommendation model

REO's current recommendations are local-first. It uses recent listening history, completion signals, artist affinity and related tracks instead of sending a user's listening history to an AI service. This keeps the app lightweight and private while still producing personalized rows.

## Important playback note

REO 3.0.0 keeps the dedicated dual-player crossfade engine in the playback service. It arms the next media item before the current item ends and crossfades the two real players, while Media3 continues to own the primary session queue.

## Build

The repository includes a GitHub Actions workflow that builds debug and unsigned release APKs on Ubuntu with Java 17 and Gradle 8.13, then packages the 3.0.0 source tree as an artifact.

## REO Music 2.0.0 — Big UI/Playback Upgrade

- YouTube Music-inspired full-screen Now Playing experience.
- Fixed album-art card sizing so artwork has a real square layout instead of collapsing to zero height.
- Removed the old waveform visualizer and its permission; this also removes the persistent `-------` artifact.
- Up Next now previews up to 12 tracks instead of 5.
- Direct playlists/search queues can stage up to 50 upcoming tracks.
- Smart Shuffle resolves up to 12 related tracks per refill.
- Larger, cleaner queue rows and a dedicated View all queue action.
- Material/dynamic theme architecture and Media3 playback remain intact.


## REO Music 2.2.0 — UI/UX Evolution

This release is a large interface pass inspired by the interaction patterns of modern music clients, including YouTube Music, while retaining REO's own branding and playback stack.

- Reworked Home with personalized hero, Listen again, Quick picks, discovery rows and shortcut actions.
- Reworked Search with recent searches, search filters, 50-result retrieval, artist/album/playlist views and clearer empty/loading states.
- Reworked Library with compact media sections and playlist-first navigation.
- Added swipe navigation between primary tabs.
- Preserved long-press and gesture controls from 2.1.0.
- Expanded smart queue look-ahead to 20 candidates and queue preview to 15 upcoming tracks.
- Improved selected navigation states and touch targets.
- Kept Media3 playback, downloads, lyrics, cache and provider architecture intact.
