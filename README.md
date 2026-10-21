# SecretLock 🔒

> **Lock smarter. Unlock your way.**  
> *Stealth hardware device locking with zero-latency enforcement and seamless Bluetooth disarming.*

[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0-purple.svg)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Build](https://img.shields.io/badge/Build-Gradle%20Passing-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

---

## 📖 Overview

**SecretLock** is an advanced Android physical security application designed for zero-trust device control.

### 🛑 The Problem: Device PINs Aren't Enough
Friends, roommates, family members, or shoulder-surfers often know or easily glance at your lock screen PIN or pattern. Once someone knows your passcode, traditional Android lock screens provide zero protection. 

### 🛡️ The SecretLock Solution: Zero-Trust Bluetooth Defense
SecretLock renders your device completely inaccessible—**even to people who know your screen PIN or passcode**. 

When armed, SecretLock enforces an **instant 0ms hardware re-lock** the exact millisecond the display is turned on. It acts as an invisible gatekeeper: the phone will immediately shut off access unless **your authorized Bluetooth hardware key** (such as paired earbuds, headphones, smartwatch, or audio receiver) connects to the phone, or the automatic **failsafe countdown timer** elapses.

The app is built entirely with modern **Jetpack Compose (Material 3)**, adheres to Android architecture best practices, and operates **100% offline** with zero network permissions.

---

## ✨ Key Features

### ⚡ 1. 0ms Instant Hardware Re-Locking
- Utilizes Android's `AccessibilityService` API (`performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`).
- Captures `Intent.ACTION_SCREEN_ON` at the kernel/system level.
- Keeps CPU execution ready using optimized wake locks and silent sub-audible audio keep-alive to guarantee immediate 0ms response without root access.

### 🎧 2. Bluetooth Hardware Disarming & Whitelist
- **Stealth Disarm**: Simply turning on your authorized Bluetooth earphone or headphones instantly disarms the lock when connected.
- **Whitelist Management**: Authorize specific devices from a dedicated management screen. Unauthorized devices cannot disarm your phone even if paired.
- **Dynamic Device Support**: Automatically adapts layout for single or multiple simultaneous connections (e.g., dual audio headphones).
- **Proactive Permissions**: Self-guided `BLUETOOTH_CONNECT` permission checks tailored for Android 12 through Android 15+.

### ⏱️ 3. Configurable Failsafe Timeout
- Prevents permanent lockout in case your Bluetooth accessories run out of battery or are misplaced.
- Customizable timeout intervals: **5 min, 10 min, 30 min, 1 hr, 2 hr, 3 hr, 6 hr, 12 hr, 24 hr**.
- Interactive Modal Bottom Sheet picker with persistent user preferences (`SecretLockPrefs`).

### 🛡️ 4. In-App PIN Lock & Advanced Lifecycle Manager
- **Settings & Config Gate**: Protect SecretLock settings behind a 4-digit numeric PIN keypad.
- **1-Minute Background Timeout**: Exiting the app keeps it unlocked for 60 seconds. Returning after 1 minute automatically locks the app and prompts for the PIN.
- **Instant Screen-OFF Lock**: Pressing the physical power button or letting the phone display turn off locks the app immediately without delay.
- **Transparent Navigation**: Moving between `MainActivity`, `SettingsActivity`, and `BluetoothDevicesActivity` preserves active sessions seamlessly.

### 📱 5. Home Screen 1-Tap Widget
- Quick arm/disarm widget right on your Android launcher.
- Custom lock glyph icon with real-time status indication (Inactive vs. Active).

### 🎨 6. Modern Material 3 Jetpack Compose UI
- Custom aesthetic palette: Soft off-white background (`#F5F6F2`), charcoal headers (`#1A1C19`), deep forest green action buttons (`#2D3932`), and soft sage badges (`#E5EDE6`).
- Edge-to-edge layout with full support for system gestures and dynamic light theme styling.

---

## 🏗️ Architecture & Component Overview

```
com.example.secretlock/
│
├── MainActivity.kt               # Main dashboard, permission checks & PIN gate
├── SettingsActivity.kt           # App lock PIN settings, whitelist link & About info
├── BluetoothDevicesActivity.kt   # Paired/Connected Bluetooth device manager & whitelist
├── AppLockManager.kt             # In-app PIN security lifecycle & 1-min timeout manager
├── SecretLockService.kt          # Background Accessibility Service (0ms locking & BT receiver)
├── SecretLockPrefs.kt            # SharedPreferences manager (Failsafe, PIN, Whitelist)
├── SecretLockWidgetProvider.kt   # AppWidget provider for 1-tap home screen toggle
└── ui/theme/                     # Color palette, Material 3 typography & themes
```

### Component Breakdown:

| Component | Responsibility |
|:---|:---|
| **`SecretLockService`** | Background accessibility engine. Enforces instantaneous display re-locking upon `ACTION_SCREEN_ON`, monitors Bluetooth connection broadcasts (`BluetoothDevice.ACTION_ACL_CONNECTED`), and manages failsafe countdowns. |
| **`AppLockManager`** | Manages in-memory app security states, background timeout timestamps, screen-off listeners, and activity navigation transitions. |
| **`BluetoothDevicesActivity`** | Discovers paired and connected Bluetooth devices via `BluetoothManager` and `AudioManager`, allowing users to whitelist authorized unlocking keys. |
| **`SecretLockWidgetProvider`** | Handles broadcast events from home screen widgets, updating UI states and sending activation commands to the service. |
| **`SecretLockPrefs`** | Encapsulates all persistent user configurations (Failsafe timeout, App Lock PIN, Authorized MAC address whitelist). |

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or newer
- **JDK 17** or **JDK 21**
- **Android SDK Platform 35**
- Target Device: **Android 10 (API 29)** or higher (Tested on Android 15)

### Build from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/SecretLock.git
   cd SecretLock
   ```

2. **Build the Debug APK using Gradle:**
   ```powershell
   .\gradlew.bat assembleDebug
   ```

3. **Install to connected device via ADB:**
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

---

## ⚙️ Initial Setup & Permissions

To allow SecretLock to enforce hardware-level locking and detect Bluetooth accessories, two system permissions must be granted:

1. **Enable Accessibility Service:**
   - Open **Settings** → **Accessibility** → **Secret Lock**.
   - Toggle the switch to **On** and confirm system authorization.
   - *(The app provides a direct shortcut from the top menu).*

2. **Grant Nearby Devices (Bluetooth) Permission:**
   - On Android 12+, navigate to **Manage Authorized Devices** in the app.
   - Tap **Grant Permission** to permit detection of connected Bluetooth accessories.

3. **Configure Your Authorized Device:**
   - Connect your Bluetooth earphones/headphones to the phone.
   - Open **Settings** → **Authorized Devices** inside SecretLock.
   - Tap **Authorize** on your device card to whitelist it.

4. **(Optional) Set an App Lock PIN:**
   - Open **Settings** → **App Lock (PIN)**.
   - Enter a 4-digit PIN. Your settings are now protected behind biometric/PIN security with a 1-minute auto-timeout.

---

## 🔒 Security & Privacy Philosophy

- **Zero Network Activity**: SecretLock requests **no internet permissions** (`android.permission.INTERNET` is omitted from `AndroidManifest.xml`).
- **Local-Only Storage**: All settings, device MAC addresses, and PIN hashes are kept strictly on-device in private SharedPreferences.
- **Fail-Safe Integrity**: The built-in failsafe timer prevents permanent locking, ensuring user access is always recoverable.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
