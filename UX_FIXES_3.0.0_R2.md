# REO Music 3.0.0 UI/UX Stabilization Pass

## Fixed in this pass

- Replaced legacy AlertDialog track menus with Material 3 bottom-sheet action surfaces.
- Replaced the legacy sleep-timer AlertDialog with a bottom-sheet selector.
- Made long-press actions consistent across home cards, search rows, queue rows, and queue previews, with haptic feedback.
- Converted Made for you from a long vertical list into a horizontal discovery rail.
- Home personalization is now local-first: recent tracks render immediately instead of waiting for network recommendations.
- Personalized provider requests are performed concurrently and the discovery footprint was reduced to avoid a very long Home feed.
- Added queue de-duplication for automatically generated and restored queues.
- Prevented explicit Add to queue / Play next actions from inserting an already queued track.
- Filtered generated recommendations to music-like tracks and rejected common video-only results such as visualizers, music videos, trailers, interviews, podcasts, reactions, vlogs, shorts, and episodes.
- Removed duplicate search results by video ID.
- Queue preview now hides duplicate media IDs.

## Architecture

No accounts, authentication, cloud profiles, Firebase, Supabase, or server-side listener database were added. Personalization and queue state remain local to the device.

## Build note

The source was statically checked and XML resources were parsed in the available environment. The Android Gradle toolchain is not installed in this execution environment, so the repository's GitHub Actions workflow remains the authoritative Gradle build check.
