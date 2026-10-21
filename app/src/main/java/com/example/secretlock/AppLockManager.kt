package com.example.secretlock

import android.content.Context

/**
 * AppLockManager manages the in-app security lifecycle:
 * - 1-minute background auto-lock timeout.
 * - Instant lock when the phone screen turns OFF.
 * - Seamless internal navigation (between MainActivity, SettingsActivity, and BluetoothDevicesActivity).
 */
object AppLockManager {
    // 1-minute auto-lock timeout as requested
    const val AUTO_LOCK_TIMEOUT_MS = 60 * 1000L

    @Volatile
    var isAppUnlocked: Boolean = false

    @Volatile
    var lastBackgroundTime: Long = 0L

    @Volatile
    var isInternalNavigation: Boolean = false

    fun checkAndRefreshLockStatus(context: Context): Boolean {
        if (!SecretLockPrefs.isAppLockEnabled(context)) {
            isAppUnlocked = true
            return true
        }

        // If navigating internally between activities, keep current state
        if (isInternalNavigation) {
            isInternalNavigation = false
            return isAppUnlocked
        }

        // Check if more than 1 minute passed in background
        val now = System.currentTimeMillis()
        if (lastBackgroundTime > 0L && (now - lastBackgroundTime) >= AUTO_LOCK_TIMEOUT_MS) {
            isAppUnlocked = false
        }
        // Reset background timestamp once foregrounded
        lastBackgroundTime = 0L

        return isAppUnlocked
    }

    fun onAppBackgrounded() {
        if (!isInternalNavigation) {
            lastBackgroundTime = System.currentTimeMillis()
        }
    }

    fun onScreenOff() {
        // Immediately lock app when display turns off or phone is locked
        isAppUnlocked = false
        lastBackgroundTime = 0L
    }

    fun unlock() {
        isAppUnlocked = true
        lastBackgroundTime = 0L
    }

    fun lock() {
        isAppUnlocked = false
        lastBackgroundTime = 0L
    }
}
