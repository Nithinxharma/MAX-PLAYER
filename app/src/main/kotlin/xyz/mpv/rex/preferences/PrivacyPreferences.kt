package xyz.mpv.rex.preferences

import xyz.mpv.rex.preferences.preference.PreferenceStore

/**
 * Privacy preferences for history, tracking, analytics, and telemetry controls.
 */
class PrivacyPreferences(
    preferenceStore: PreferenceStore,
) {
    val pauseWatchHistory = preferenceStore.getBoolean("privacy_pause_watch_history", false)
    val pauseSearchHistory = preferenceStore.getBoolean("privacy_pause_search_history", false)
    val sendCrashReports = preferenceStore.getBoolean("privacy_send_crash_reports", true)
    val anonymousAnalytics = preferenceStore.getBoolean("privacy_anonymous_analytics", false)
    val biometricLockApp = preferenceStore.getBoolean("privacy_biometric_lock", false)
}
