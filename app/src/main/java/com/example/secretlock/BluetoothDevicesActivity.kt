package com.example.secretlock

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private val BgOffWhite = Color(0xFFF5F6F2)
private val TextCharcoal = Color(0xFF1A1C19)
private val TextSubtitle = Color(0xFF6C726B)
private val CardWhite = Color(0xFFFFFFFF)
private val AccentSoftGreen = Color(0xFFE5EDE6)
private val DarkForestGreen = Color(0xFF2D3932)
private val FieldBg = Color(0xFFF1F4EE)
private val StatusActive = Color(0xFF2E7D32)

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean,
)

class BluetoothDevicesActivity : ComponentActivity() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                AppLockManager.onScreenOff()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        setContent {
            SecretLockTheme {
                BluetoothDevicesScreen(onBack = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (SecretLockPrefs.isAppLockEnabled(this) && !AppLockManager.checkAndRefreshLockStatus(this)) {
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.onAppBackgrounded()
    }

    override fun finish() {
        if (AppLockManager.isAppUnlocked) {
            AppLockManager.isInternalNavigation = true
        }
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }

    companion object {
        fun queryBluetoothDevices(context: Context): List<DiscoveredBluetoothDevice> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return emptyList()
            }

            return try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = bluetoothManager?.adapter ?: return emptyList()

                // Check connected audio devices via AudioManager
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val connectedAudioNames = mutableSetOf<String>()
                audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.forEach { device ->
                    if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    ) {
                        connectedAudioNames.add(device.productName.toString().trim())
                    }
                }

                // Try to check profile connection state
                val connectedGattDevices = mutableSetOf<String>()
                try {
                    bluetoothManager.getConnectedDevices(BluetoothProfile.GATT).forEach {
                        connectedGattDevices.add(it.address)
                    }
                } catch (_: Exception) {}

                val bonded = adapter.bondedDevices ?: emptySet()
                bonded.map { device ->
                    val devName = device.name ?: "Bluetooth Device"
                    val devAddress = device.address
                    val isConnected = connectedGattDevices.contains(devAddress) ||
                            connectedAudioNames.any { it.contains(devName, ignoreCase = true) || devName.contains(it, ignoreCase = true) }

                    DiscoveredBluetoothDevice(
                        name = devName,
                        address = devAddress,
                        isConnected = isConnected,
                    )
                }.sortedWith(compareByDescending<DiscoveredBluetoothDevice> { it.isConnected }.thenBy { it.name })
            } catch (_: SecurityException) {
                emptyList()
            }
        }
    }
}

@Composable
fun BluetoothDevicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    var devicesList by remember {
        mutableStateOf(BluetoothDevicesActivity.queryBluetoothDevices(context))
    }

    var authorizedAddresses by remember {
        mutableStateOf(SecretLockPrefs.getAuthorizedDevices(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                devicesList = BluetoothDevicesActivity.queryBluetoothDevices(context)
            } else {
                Toast.makeText(context, "Bluetooth permission required to view devices", Toast.LENGTH_LONG).show()
            }
        },
    )

    // Request permission immediately if missing
    LaunchedEffect(Unit) {
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            devicesList = BluetoothDevicesActivity.queryBluetoothDevices(context)
        }
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
            // Header: Back Button + Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextCharcoal,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Bluetooth Devices",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextCharcoal,
                    )
                    Text(
                        text = "Authorize devices to disarm Secret Lock",
                        fontSize = 13.sp,
                        color = TextSubtitle,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Permission Warning Card if missing
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Permission Required",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCharcoal,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android requires Nearby Devices permission to detect your connected Bluetooth devices.",
                            fontSize = 13.sp,
                            color = TextSubtitle,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkForestGreen),
                        ) {
                            Text("Grant Permission", color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Summary Info Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (authorizedAddresses.isEmpty()) {
                            "Status: Any paired device can unlock"
                        } else {
                            "Status: Whitelist active (${authorizedAddresses.size} authorized)"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (authorizedAddresses.isEmpty()) TextCharcoal else StatusActive,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (authorizedAddresses.isEmpty()) {
                            "Tap 'Authorize' on your device to restrict unlocking to that device only."
                        } else {
                            "Only authorized devices will disarm Secret Lock when connected."
                        },
                        fontSize = 12.sp,
                        color = TextSubtitle,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Devices Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Paired & Connected Devices (${devicesList.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal,
                )

                Text(
                    text = "Refresh",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkForestGreen,
                    modifier = Modifier.clickable {
                        devicesList = BluetoothDevicesActivity.queryBluetoothDevices(context)
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Devices List
            if (devicesList.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = TextSubtitle,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Bluetooth Devices Found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextCharcoal,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pair your device in phone settings to see it here.",
                            fontSize = 13.sp,
                            color = TextSubtitle,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    devicesList.forEach { device ->
                        val isAuthorized = authorizedAddresses.contains(device.address)

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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
                                            .background(if (device.isConnected) AccentSoftGreen else FieldBg),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Headphones,
                                            contentDescription = null,
                                            tint = if (device.isConnected) DarkForestGreen else TextSubtitle,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = device.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextCharcoal,
                                            )
                                            if (device.isConnected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(AccentSoftGreen)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        text = "Connected",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = DarkForestGreen,
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = device.address,
                                            fontSize = 12.sp,
                                            color = TextSubtitle,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                if (isAuthorized) {
                                    Button(
                                        onClick = {
                                            SecretLockPrefs.toggleDeviceAuthorization(context, device.address)
                                            authorizedAddresses = SecretLockPrefs.getAuthorizedDevices(context)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DarkForestGreen,
                                            contentColor = Color.White,
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 6.dp,
                                        ),
                                        modifier = Modifier.height(38.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Authorized",
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Authorized",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            SecretLockPrefs.toggleDeviceAuthorization(context, device.address)
                                            authorizedAddresses = SecretLockPrefs.getAuthorizedDevices(context)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 14.dp,
                                            vertical = 6.dp,
                                        ),
                                        modifier = Modifier.height(38.dp),
                                    ) {
                                        Text(
                                            text = "Authorize",
                                            fontSize = 12.sp,
                                            color = DarkForestGreen,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Open Phone Bluetooth Settings button
            Button(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FieldBg,
                    contentColor = TextCharcoal,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TextCharcoal,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pair New Device in Phone Settings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextCharcoal,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
