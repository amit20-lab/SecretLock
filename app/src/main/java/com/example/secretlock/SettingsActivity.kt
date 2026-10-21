package com.example.secretlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.secretlock.ui.theme.SecretLockTheme

private val BgOffWhite = Color(0xFFF5F6F2)
private val TextCharcoal = Color(0xFF1A1C19)
private val TextSubtitle = Color(0xFF6C726B)
private val CardWhite = Color(0xFFFFFFFF)
private val AccentSoftGreen = Color(0xFFE5EDE6)
private val DarkForestGreen = Color(0xFF2D3932)
private val FieldBg = Color(0xFFF1F4EE)

class SettingsActivity : ComponentActivity() {

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
                SettingsScreen(onBack = { finish() })
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
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isAppLockEnabled by remember {
        mutableStateOf(SecretLockPrefs.isAppLockEnabled(context))
    }

    var authorizedCount by remember {
        mutableStateOf(SecretLockPrefs.getAuthorizedDevices(context).size)
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

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

                Text(
                    text = "Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Card 1: App Lock PIN Protection
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
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "App Lock Icon",
                                    tint = DarkForestGreen,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Lock App with PIN",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCharcoal,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Require PIN to open and configure Secret Lock.",
                                    fontSize = 12.sp,
                                    color = TextSubtitle,
                                    lineHeight = 16.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = isAppLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // Open PIN setup dialog if enabling
                                    pinInput = ""
                                    pinError = ""
                                    showPinDialog = true
                                } else {
                                    isAppLockEnabled = false
                                    SecretLockPrefs.setAppLockEnabled(context, false)
                                    SecretLockPrefs.setAppPin(context, "")
                                    Toast.makeText(context, "App PIN disabled", Toast.LENGTH_SHORT).show()
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

                    if (isAppLockEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFECEFEA))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FieldBg)
                                .clickable {
                                    pinInput = ""
                                    pinError = ""
                                    showPinDialog = true
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Change PIN Code",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkForestGreen,
                            )

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = DarkForestGreen,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Authorized Devices Card (Navigates to BluetoothDevicesActivity)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        AppLockManager.isInternalNavigation = true
                        context.startActivity(Intent(context, BluetoothDevicesActivity::class.java))
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
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
                                contentDescription = "Bluetooth Devices",
                                tint = DarkForestGreen,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Authorized Devices",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCharcoal,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (authorizedCount > 0) {
                                    "$authorizedCount device(s) authorized to unlock"
                                } else {
                                    "Manage paired & authorized devices"
                                },
                                fontSize = 12.sp,
                                color = TextSubtitle,
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open",
                        tint = TextSubtitle,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card 3: About Section at Bottom
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentSoftGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About Icon",
                                tint = DarkForestGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "About Secret Lock",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCharcoal,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1-line Motto and Goal
                    Text(
                        text = "Secret Lock: Empowering private device control with stealth hardware unlocking and zero-friction security.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextCharcoal,
                        lineHeight = 19.sp,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Version 1.0 • Built for Android 15",
                        fontSize = 11.sp,
                        color = TextSubtitle,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Set PIN Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                isAppLockEnabled = SecretLockPrefs.isAppLockEnabled(context)
            },
            title = {
                Text(
                    text = "Set 4-Digit App PIN",
                    fontWeight = FontWeight.Bold,
                    color = TextCharcoal,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose a 4-digit PIN to secure Secret Lock against unauthorized access.",
                        fontSize = 13.sp,
                        color = TextSubtitle,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pinInput = it
                                pinError = ""
                            }
                        },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (pinError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pinError,
                            fontSize = 12.sp,
                            color = Color.Red,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            SecretLockPrefs.setAppPin(context, pinInput)
                            SecretLockPrefs.setAppLockEnabled(context, true)
                            isAppLockEnabled = true
                            showPinDialog = false
                            Toast.makeText(context, "App PIN saved successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            pinError = "PIN must be exactly 4 digits"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkForestGreen),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Save PIN", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        isAppLockEnabled = SecretLockPrefs.isAppLockEnabled(context)
                    },
                ) {
                    Text("Cancel", color = TextSubtitle)
                }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp),
        )
    }
}
