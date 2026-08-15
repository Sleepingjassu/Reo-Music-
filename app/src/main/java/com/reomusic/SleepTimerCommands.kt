package com.reomusic

/**
 * Custom MediaSession commands used to control the sleep timer, which
 * lives inside [PlaybackService] so it keeps working even if the app is
 * backgrounded or the screen is locked (the whole point of a sleep timer).
 */
object SleepTimerCommands {
    const val ACTION_SET_DURATION = "com.reomusic.SLEEP_TIMER_SET_DURATION"
    const val ACTION_SET_END_OF_TRACK = "com.reomusic.SLEEP_TIMER_END_OF_TRACK"
    const val ACTION_CANCEL = "com.reomusic.SLEEP_TIMER_CANCEL"
    const val EXTRA_DURATION_MS = "duration_ms"
}
