package com.example.secretlock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.secretlock.ui.theme.SecretLockTheme
import kotlinx.coroutines.launch

private val BgOffWhite = Color(0xFFF5F6F2)
private val TextCharcoal = Color(0xFF1A1C19)
private val TextSubtitle = Color(0xFF6C726B)
private val CardWhite = Color(0xFFFFFFFF)
private val AccentSoftGreen = Color(0xFFE5EDE6)
private val DarkForestGreen = Color(0xFF2D3932)
private val FieldBg = Color(0xFFF1F4EE)
private val StatusInactive = Color(0xFF8F948E)
private val StatusActive = Color(0xFF2E7D32)
private val WarningBannerBg = Color(0xFFFFF3CD)
private val WarningBannerText = Color(0xFF856404)

data class FailsafeItem(val minutes: Int, val label: String)

val failsafeChoices = listOf(
    FailsafeItem(5, "5 minutes"),
    FailsafeItem(10, "10 minutes"),
    FailsafeItem(30, "30 minutes"),
    FailsafeItem(60, "1 hour"),
    FailsafeItem(120, "2 hours"),
    FailsafeItem(180, "3 hours"),
    FailsafeItem(360, "6 hours"),
    FailsafeItem(720, "12 hours"),
    FailsafeItem(1440, "24 hours"),
)

class MainActivity : ComponentActivity() {

    private var isLockActiveState = mutableStateOf(false)
    private var isAccessibilityEnabledState = mutableStateOf(false)

    // In-memory unlock flag for App Lock PIN
    private var isAppUnlockedState = mutableStateOf(false)

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    AppLockManager.onScreenOff()
                    isAppUnlockedState.value = false
                }
                SecretLockService.ACTION_STATE_CHANGED -> {
                    isLockActiveState.value = SecretLockService.isSecretLockActive
                    checkAccessibilityStatus()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter().apply {
            addAction(SecretLockService.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        isAppUnlockedState.value = AppLockManager.checkAndRefreshLockStatus(this)

        setContent {
            SecretLockTheme {
                val isPinEnabled = SecretLockPrefs.isAppLockEnabled(this)
                val isUnlocked = isAppUnlockedState.value

                if (isPinEnabled && !isUnlocked) {
                    AppPinLockScreen(
                        onPinCorrect = {
                            AppLockManager.unlock()
                            isAppUnlockedState.value = true
                        },
                    )
                } else {
                    SecretLockApp(
                        isLockActive = isLockActiveState.value,
                        isAccessibilityEnabled = isAccessibilityEnabledState.value,
                        onActivateClick = { activateLock() },
                        onDeactivateClick = { deactivateLock() },
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                        onOpenSettings = {
                            AppLockManager.isInternalNavigation = true
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onOpenBluetoothDevices = {
                            AppLockManager.isInternalNavigation = true
                            startActivity(Intent(this, BluetoothDevicesActivity::class.java))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isLockActiveState.value = SecretLockService.isSecretLockActive
        checkAccessibilityStatus()
        isAppUnlockedState.value = AppLockManager.checkAndRefreshLockStatus(this)
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.onAppBackgrounded()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
    }

    private fun checkAccessibilityStatus() {
        isAccessibilityEnabledState.value = isAccessibilityServiceEnabled(this)
    }

    private fun activateLock() {
        if (!isAccessibilityEnabledState.value) {
            Toast.makeText(
                this,
                "Please enable Secret Lock Accessibility Service first",
                Toast.LENGTH_LONG,
            ).show()
            openAccessibilitySettings()
            return
        }

        val intent = Intent(SecretLockService.ACTION_ACTIVATE).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        isLockActiveState.value = true
    }

    private fun deactivateLock() {
        val intent = Intent(SecretLockService.ACTION_DEACTIVATE).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
        isLockActiveState.value = false
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open Accessibility Settings", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${SecretLockService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}

@Composable
fun AppPinLockScreen(onPinCorrect: () -> Unit) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgOffWhite)
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AccentSoftGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = DarkForestGreen,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Enter App PIN",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextCharcoal,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your 4-digit PIN to open Secret Lock",
                fontSize = 13.sp,
                color = TextSubtitle,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4 PIN Dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    showError -> Color.Red
                                    isFilled -> DarkForestGreen
                                    else -> Color(0xFFD6DDD7)
                                },
                            ),
                    )
                }
            }

            if (showError) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Incorrect PIN. Try again.",
                    fontSize = 12.sp,
                    color = Color.Red,
                )
            }
        }

        // Numeric Keypad
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val keypad = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL"),
            )

            keypad.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Box(modifier = Modifier.size(68.dp))
                        } else if (key == "DEL") {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                            showError = false
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete",
                                    tint = TextCharcoal,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(CardWhite)
                                    .clickable {
                                        if (enteredPin.length < 4) {
                                            enteredPin += key
                                            showError = false

                                            if (enteredPin.length == 4) {
                                                if (SecretLockPrefs.validateAppPin(context, enteredPin)) {
                                                    onPinCorrect()
                                                } else {
                                                    showError = true
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCharcoal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretLockApp(
    isLockActive: Boolean,
    isAccessibilityEnabled: Boolean,
    onActivateClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBluetoothDevices: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var failsafeMinutes by remember {
        mutableIntStateOf(SecretLockPrefs.getFailsafeMinutes(context))
    }
    var isBluetoothUnlockEnabled by remember {
        mutableStateOf(SecretLockPrefs.isBluetoothUnlockEnabled(context))
    }
    var authorizedAddresses by remember {
        mutableStateOf(SecretLockPrefs.getAuthorizedDevices(context))
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                isBluetoothUnlockEnabled = true
                SecretLockPrefs.setBluetoothUnlockEnabled(context, true)
                Toast.makeText(context, "Bluetooth auto-unlock enabled", Toast.LENGTH_SHORT).show()
            } else {
                isBluetoothUnlockEnabled = false
                SecretLockPrefs.setBluetoothUnlockEnabled(context, false)
                Toast.makeText(context, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
            }
        },
    )

    // Sync on resume
    LaunchedEffect(Unit) {
        authorizedAddresses = SecretLockPrefs.getAuthorizedDevices(context)
        isBluetoothUnlockEnabled = SecretLockPrefs.isBluetoothUnlockEnabled(context)
    }

    Scaffold(
        containerColor = BgOffWhite,
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Top Bar: Title, Subtitle, and 3-dots Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Secret Lock",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lock smarter. Unlock your way.",
                        fontSize = 14.sp,
                        color = TextSubtitle,
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options Menu",
                            tint = TextCharcoal,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Accessibility Settings") },
                            onClick = {
                                menuExpanded = false
                                onOpenAccessibilitySettings()
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Accessibility Warning Banner
            if (!isAccessibilityEnabled) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningBannerBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccessibilitySettings() },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ Accessibility Service Disabled",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = WarningBannerText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Secret Lock requires Accessibility permission to lock the screen. Tap here to enable it in Settings.",
                            fontSize = 13.sp,
                            color = WarningBannerText,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Card 1: Main Secret Lock Action Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(AccentSoftGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Lock Badge",
                                tint = DarkForestGreen,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "Secret Lock",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Locks screen instantly (0ms) until an authorized device connects or failsafe timeout.",
                                fontSize = 13.sp,
                                color = TextSubtitle,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (isLockActive) {
                                onDeactivateClick()
                            } else {
                                onActivateClick()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkForestGreen,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLockActive) "Disarm Secret Lock" else "Activate Secret Lock",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Bluetooth Unlock & Authorized Devices Shortcut
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AccentSoftGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = "Bluetooth Icon",
                                    tint = DarkForestGreen,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Bluetooth Unlock",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCharcoal,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Disarm when authorized device connects.",
                                    fontSize = 12.sp,
                                    color = TextSubtitle,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = isBluetoothUnlockEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        isBluetoothUnlockEnabled = true
                                        SecretLockPrefs.setBluetoothUnlockEnabled(context, true)
                                    } else {
                                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    }
                                } else {
                                    isBluetoothUnlockEnabled = isChecked
                                    SecretLockPrefs.setBluetoothUnlockEnabled(context, isChecked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DarkForestGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFD6DDD7),
                            ),
                        )
                    }

                    // Clickable Card to open BluetoothDevicesActivity
                    if (isBluetoothUnlockEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFECEFEA))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(FieldBg)
                                .clickable { onOpenBluetoothDevices() }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Manage Authorized Devices",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCharcoal,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (authorizedAddresses.isEmpty()) {
                                        "All paired devices allowed. Tap to authorize specific device."
                                    } else {
                                        "${authorizedAddresses.size} device(s) authorized to unlock."
                                    },
                                    fontSize = 12.sp,
                                    color = TextSubtitle,
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Manage",
                                tint = DarkForestGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Failsafe Time Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Failsafe Time",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatically disables Secret Lock after this time if not unlocked.",
                        fontSize = 13.sp,
                        color = TextSubtitle,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(FieldBg)
                            .clickable { showBottomSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = "Schedule Icon",
                                tint = TextCharcoal,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = SecretLockPrefs.getFormattedFailsafeLabel(failsafeMinutes),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextCharcoal,
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Time",
                            tint = TextSubtitle,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 4: Status Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Status",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isLockActive) StatusActive else StatusInactive),
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isLockActive) "Active" else "Inactive",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextCharcoal,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isLockActive) {
                                    val authCount = authorizedAddresses.size
                                    val authText = if (authCount > 0) {
                                        "$authCount authorized device(s)"
                                    } else {
                                        "paired Bluetooth device"
                                    }
                                    "Armed. Disarms via $authText or failsafe (${SecretLockPrefs.getFormattedFailsafeLabel(failsafeMinutes)})."
                                } else {
                                    "Secret Lock is currently off."
                                },
                                fontSize = 13.sp,
                                color = TextSubtitle,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Modal Bottom Sheet for Failsafe Time Selection
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = CardWhite,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            var tempSelectedMinutes by remember { mutableIntStateOf(failsafeMinutes) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Failsafe Time",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Automatically disables Secret Lock after this time if not unlocked.",
                    fontSize = 14.sp,
                    color = TextSubtitle,
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    items(failsafeChoices) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { tempSelectedMinutes = option.minutes }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (tempSelectedMinutes == option.minutes),
                                onClick = { tempSelectedMinutes = option.minutes },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = DarkForestGreen,
                                    unselectedColor = Color(0xFFA5ACA3),
                                ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = option.label,
                                fontSize = 15.sp,
                                color = TextCharcoal,
                                fontWeight = if (tempSelectedMinutes == option.minutes) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        failsafeMinutes = tempSelectedMinutes
                        SecretLockPrefs.setFailsafeMinutes(context, tempSelectedMinutes)
                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                            showBottomSheet = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkForestGreen,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "Done",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
