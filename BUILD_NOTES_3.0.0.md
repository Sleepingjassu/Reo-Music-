# REO Music 3.0.0 build notes

This archive is a source release. GitHub Actions is configured to build the debug and unsigned release APKs with Gradle 8.13 and Java 17.

The source contains no account/authentication/database backend. Local preferences and playback state are device-only.

The deprecated MainActivity back-navigation and voice-search APIs have been replaced with AndroidX OnBackPressedDispatcher and Activity Result APIs.
