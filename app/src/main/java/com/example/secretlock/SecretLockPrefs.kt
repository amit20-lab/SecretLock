package com.example.secretlock

import android.content.Context
import android.content.SharedPreferences

/**
 * SecretLockPrefs manages user configuration for SecretLock,
 * including failsafe timeout duration, Bluetooth device authorization whitelist,
 * and App Lock PIN protection.
 */
object SecretLockPrefs {
    private const val PREFS_NAME = "secret_lock_preferences"
    private const val KEY_FAILSAFE_MINUTES = "failsafe_timeout_minutes"
    private const val KEY_UNLOCK_BLUETOOTH = "unlock_via_bluetooth_device"
    private const val KEY_AUTHORIZED_DEVICES = "authorized_bluetooth_mac_addresses"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_pin_enabled"
    private const val KEY_APP_PIN = "app_lock_pin_code"

    // Default failsafe timeout is 30 minutes, matching the UI design
    const val DEFAULT_FAILSAFE_MINUTES = 30

    fun getFailsafeMinutes(context: Context): Int {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FAILSAFE_MINUTES, DEFAULT_FAILSAFE_MINUTES)
    }

    fun setFailsafeMinutes(context: Context, minutes: Int) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FAILSAFE_MINUTES, minutes).apply()
    }

    fun getFailsafeDurationMs(context: Context): Long {
        return getFailsafeMinutes(context) * 60 * 1000L
    }

    fun isBluetoothUnlockEnabled(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_UNLOCK_BLUETOOTH, true)
    }

    fun setBluetoothUnlockEnabled(context: Context, enabled: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_UNLOCK_BLUETOOTH, enabled).apply()
    }

    fun getAuthorizedDevices(context: Context): Set<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_AUTHORIZED_DEVICES, emptySet()) ?: emptySet()
    }

    fun setAuthorizedDevices(context: Context, addresses: Set<String>) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_AUTHORIZED_DEVICES, addresses).apply()
    }

    fun toggleDeviceAuthorization(context: Context, address: String) {
        val current = getAuthorizedDevices(context).toMutableSet()
        if (current.contains(address)) {
            current.remove(address)
        } else {
            current.add(address)
        }
        setAuthorizedDevices(context, current)
    }

    fun isDeviceAuthorized(context: Context, address: String): Boolean {
        val authorized = getAuthorizedDevices(context)
        return if (authorized.isEmpty()) {
            // If user hasn't selected specific devices, allow any paired device by default
            true
        } else {
            authorized.contains(address)
        }
    }

    // App Lock PIN Settings
    fun isAppLockEnabled(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false) && getAppPin(context).isNotEmpty()
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    fun getAppPin(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_APP_PIN, "") ?: ""
    }

    fun setAppPin(context: Context, pin: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_PIN, pin).apply()
    }

    fun validateAppPin(context: Context, inputPin: String): Boolean {
        val storedPin = getAppPin(context)
        return storedPin.isNotEmpty() && storedPin == inputPin
    }

    fun getFormattedFailsafeLabel(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes minutes"
            minutes == 60 -> "1 hour"
            minutes % 60 == 0 -> "${minutes / 60} hours"
            else -> "$minutes minutes"
        }
    }
}
