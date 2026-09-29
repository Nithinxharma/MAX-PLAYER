package xyz.mpv.rex.preferences

import xyz.mpv.rex.preferences.preference.PreferenceStore

/**
 * Accessibility preferences for visual, auditory, and motor accessibility adjustments.
 */
class AccessibilityPreferences(
    preferenceStore: PreferenceStore,
) {
    val highContrast = preferenceStore.getBoolean("accessibility_high_contrast", false)
    val largeTouchTargets = preferenceStore.getBoolean("accessibility_large_touch_targets", false)
    val enhanceSubtitleReadability = preferenceStore.getBoolean("accessibility_enhance_subtitles", true)
    val hapticFeedback = preferenceStore.getBoolean("accessibility_haptic_feedback", true)
    val reducedMotion = preferenceStore.getBoolean("accessibility_reduced_motion", false)
    val screenReaderAssist = preferenceStore.getBoolean("accessibility_screen_reader_assist", false)
}
