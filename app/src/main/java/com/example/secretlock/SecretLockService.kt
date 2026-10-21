package com.example.secretlock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * SecretLockService: Accessibility Service that locks the device screen instantly (0ms)
 * and disarms when an authorized paired Bluetooth device (e.g. earphone) connects or when the
 * failsafe timeout expires.
 */
@Suppress("AccessibilityPolicy")
class SecretLockService : AccessibilityService() {

    companion object {
        private const val TAG = "SecretLockService"
        const val ACTION_WIDGET_LOCK = "com.example.secretlock.WIDGET_LOCK"
        const val ACTION_ACTIVATE = "com.example.secretlock.ACTIVATE"
        const val ACTION_DEACTIVATE = "com.example.secretlock.DEACTIVATE"
        const val ACTION_STATE_CHANGED = "com.example.secretlock.STATE_CHANGED"

        @Volatile
        var isSecretLockActive: Boolean = false
            private set

        @Volatile
        var sessionExpiryTime: Long = 0L
            private set

        @Volatile
        var isServiceConnected: Boolean = false
            private set
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isReceiverRegistered: Boolean = false

    private val sessionHandler = Handler(Looper.getMainLooper())
    private val sessionTimeoutRunnable = Runnable {
        Log.i(TAG, "Failsafe timeout reached. Automatically disarming Secret Lock.")
        deactivateSecretLock()
    }

    private val lockBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen turned ON. isSecretLockActive=$isSecretLockActive")
                    if (checkSessionExpiry()) return
                    if (isSecretLockActive) {
                        lockScreen()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned OFF. isSecretLockActive=$isSecretLockActive")
                    if (checkSessionExpiry()) return
                    if (isSecretLockActive) {
                        val remainingMs = (sessionExpiryTime - System.currentTimeMillis()).coerceAtLeast(60_000L)
                        acquireWakeLock(remainingMs + 30_000L)
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d(TAG, "Broadcast: BluetoothDevice.ACTION_ACL_CONNECTED")
                    handleBluetoothConnected(intent)
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    Log.d(TAG, "Broadcast: BluetoothA2dp state=$state")
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        handleBluetoothConnected(intent)
                    }
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                    Log.d(TAG, "Broadcast: BluetoothHeadset state=$state")
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        handleBluetoothConnected(intent)
                    }
                }
                ACTION_WIDGET_LOCK, ACTION_ACTIVATE -> {
                    Log.d(TAG, "Activation request received. Starting Secret Lock session.")
                    activateSecretLock()
                }
                ACTION_DEACTIVATE -> {
                    Log.d(TAG, "Manual deactivation request received.")
                    deactivateSecretLock()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo = info

        isServiceConnected = true
        registerLockReceiver()

        notifyStateChanged()
        Log.i(TAG, "SecretLockService connected and ready.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
        sessionHandler.removeCallbacks(sessionTimeoutRunnable)
        unregisterLockReceiver()
        releaseWakeLock()
        notifyStateChanged()
        Log.i(TAG, "SecretLockService destroyed.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event processing not needed for Bluetooth unlock
    }

    override fun onInterrupt() {
        Log.w(TAG, "SecretLockService interrupted.")
    }

    /**
     * Triggered when any Bluetooth audio device connects. Disarms Secret Lock if device is authorized.
     */
    private fun handleBluetoothConnected(intent: Intent?) {
        if (!isSecretLockActive) return
        if (!SecretLockPrefs.isBluetoothUnlockEnabled(this)) {
            Log.d(TAG, "Bluetooth device connected, but Bluetooth unlock toggle is OFF.")
            return
        }

        val connectedDevice: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val address = connectedDevice?.address
        val name = try {
            connectedDevice?.name ?: "Bluetooth Device"
        } catch (_: SecurityException) {
            "Bluetooth Device"
        }

        if (address != null) {
            if (SecretLockPrefs.isDeviceAuthorized(this, address)) {
                Log.i(TAG, "Authorized Bluetooth device connected: $name ($address)! Disarming Secret Lock.")
                deactivateSecretLock()
            } else {
                Log.w(TAG, "Ignored connection from unauthorized Bluetooth device: $name ($address)")
            }
        } else {
            Log.i(TAG, "Bluetooth connection event received. Disarming Secret Lock.")
            deactivateSecretLock()
        }
    }

    /**
     * Checks if current failsafe session has expired. If so, disarms automatically.
     */
    private fun checkSessionExpiry(): Boolean {
        if (isSecretLockActive && sessionExpiryTime > 0L && System.currentTimeMillis() >= sessionExpiryTime) {
            Log.i(TAG, "Session expired based on clock check. Disarming.")
            deactivateSecretLock()
            return true
        }
        return false
    }

    /**
     * Activates Secret Lock with user-configured failsafe duration.
     */
    fun activateSecretLock() {
        val durationMs = SecretLockPrefs.getFailsafeDurationMs(this)
        isSecretLockActive = true
        sessionExpiryTime = System.currentTimeMillis() + durationMs

        sessionHandler.removeCallbacks(sessionTimeoutRunnable)
        sessionHandler.postDelayed(sessionTimeoutRunnable, durationMs)

        acquireWakeLock(durationMs + 60_000L)
        notifyStateChanged()

        Log.i(TAG, "Secret Lock armed for ${SecretLockPrefs.getFormattedFailsafeLabel(SecretLockPrefs.getFailsafeMinutes(this))}.")
        lockScreen()
    }

    /**
     * Disarms Secret Lock, restores normal device state, and cancels failsafe timer.
     */
    fun deactivateSecretLock() {
        isSecretLockActive = false
        sessionExpiryTime = 0L
        sessionHandler.removeCallbacks(sessionTimeoutRunnable)

        releaseWakeLock()
        notifyStateChanged()
        Log.i(TAG, "Secret Lock disarmed.")
    }

    /**
     * Dispatches state update broadcast to keep UI and widgets synced.
     */
    private fun notifyStateChanged() {
        try {
            val intent = Intent(ACTION_STATE_CHANGED).apply {
                setPackage(packageName)
                putExtra("isSecretLockActive", isSecretLockActive)
                putExtra("sessionExpiryTime", sessionExpiryTime)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast state change", e)
        }
    }

    /**
     * Locks device screen immediately via AccessibilityService.
     */
    private fun lockScreen() {
        Log.d(TAG, "Calling performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)")
        val success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (!success) {
            Log.w(TAG, "GLOBAL_ACTION_LOCK_SCREEN returned false.")
        }
    }

    /**
     * Holds CPU wake lock for the active session duration.
     */
    private fun acquireWakeLock(timeoutMs: Long) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SecretLock::PartialWakeLock",
            ).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
            Log.d(TAG, "PARTIAL_WAKE_LOCK acquired for ${timeoutMs / 1000}s.")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring PARTIAL_WAKE_LOCK", e)
        }
    }

    /**
     * Releases active CPU wake lock.
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "PARTIAL_WAKE_LOCK released.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing PARTIAL_WAKE_LOCK", e)
        }
    }

    /**
     * Registers system screen, Bluetooth, and custom action broadcast receiver.
     */
    private fun registerLockReceiver() {
        if (isReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ACTION_WIDGET_LOCK)
            addAction(ACTION_ACTIVATE)
            addAction(ACTION_DEACTIVATE)
        }

        ContextCompat.registerReceiver(
            this,
            lockBroadcastReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        isReceiverRegistered = true
        Log.d(TAG, "Lock & Bluetooth BroadcastReceiver registered.")
    }

    /**
     * Unregisters broadcast receiver safely.
     */
    private fun unregisterLockReceiver() {
        if (!isReceiverRegistered) return
        try {
            unregisterReceiver(lockBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered.", e)
        } finally {
            isReceiverRegistered = false
        }
    }
}
