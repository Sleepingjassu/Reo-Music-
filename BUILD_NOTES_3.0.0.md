# REO Music 3.0.0 build notes

This archive is a source release. GitHub Actions is configured to build the debug and unsigned release APKs with Gradle 8.13 and Java 17.

The source contains no account/authentication/database backend. Local preferences and playback state are device-only.

The deprecated MainActivity back-navigation and voice-search APIs have been replaced with AndroidX OnBackPressedDispatcher and Activity Result APIs.

## UX-R3 build fixes
- Fixed `async`/coroutine-scope compilation errors in `MainActivity.kt`.
- Home personalization requests now use structured concurrency and cancel correctly.
- Made-for-you cards are deduplicated by media ID.
- Explicit Play All / Shuffle playback no longer races with automatic related-track queue generation.
- Explicit playlist/library queues are deduplicated and filtered for music-like results before playback.
- Queue construction is capped and resolved concurrently for faster startup.
