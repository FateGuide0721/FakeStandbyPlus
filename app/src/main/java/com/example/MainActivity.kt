package com.example

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.adb.AdbManager
import com.example.data.AppConfig
import com.example.data.PreferencesManager
import com.example.receiver.FakeStandbyAdminReceiver
import com.example.service.AdbPairingService
import com.example.service.FakeStandbyService
import com.example.ui.theme.FakeStandbyTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = isSystemInDarkTheme()

            // Dynamic status bar style according to Material Design 3 light/dark mode
            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            FakeStandbyTheme(darkTheme = isDark) {
                FakeStandbyMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeStandbyMainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefsManager = remember { PreferencesManager.getInstance(context) }
    val adbManager = remember { AdbManager.getInstance(context) }

    val config by prefsManager.config.collectAsState()
    val isStandbyActive by FakeStandbyService.isStandbyActive.collectAsState()
    val isAdbPaired by adbManager.isPaired.collectAsState()
    val isAdbConnected by adbManager.isConnected.collectAsState()
    val isSearchingPairing by adbManager.isSearchingPairing.collectAsState()
    val discoveredPairingPort by adbManager.discoveredPairingPort.collectAsState()
    val adbStatusMessage by adbManager.statusMessage.collectAsState()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryIgnoring by remember {
        mutableStateOf(valPowerManager(context).isIgnoringBatteryOptimizations(context.packageName))
    }
    var isDeviceAdminActive by remember {
        mutableStateOf(isDeviceAdminEnabled(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isBatteryIgnoring = valPowerManager(context).isIgnoringBatteryOptimizations(context.packageName)
                isDeviceAdminActive = isDeviceAdminEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "FakeStandby",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Master Action Hero Card (Modified: removed small text under button)
            item(key = "hero_card") {
                MasterStatusHeroCard(
                    isStandbyActive = isStandbyActive,
                    hasOverlayPermission = hasOverlayPermission,
                    isAdbPaired = isAdbPaired,
                    onToggleStandby = {
                        if (!hasOverlayPermission) {
                            requestOverlayPermission(context)
                        } else {
                            if (isStandbyActive) {
                                FakeStandbyService.stop(context)
                            } else {
                                FakeStandbyService.start(context)
                            }
                        }
                    }
                )
            }

            // Wireless ADB Section (Modified: with 1-click authorize and direct jump to wireless debugging)
            item(key = "adb_wireless_card") {
                WirelessAdbSection(
                    isPaired = isAdbPaired,
                    isConnected = isAdbConnected,
                    isSearching = isSearchingPairing,
                    discoveredPairingPort = discoveredPairingPort,
                    statusMessage = adbStatusMessage,
                    savedConnectPort = config.adbConnectPort,
                    onStartPairingSearch = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        AdbPairingService.startSearch(context)
                    },
                    onStopPairingSearch = {
                        AdbPairingService.stopSearch(context)
                    },
                    onSubmitPairCode = { port, code ->
                        adbManager.pair(port, code)
                    },
                    onAutoAuthorize = {
                        val res = adbManager.autoAuthorize()
                        if (res.isFailure) {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "获取授权失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onManualConnect = { port ->
                        adbManager.connect(port)
                    },
                    onOpenWirelessSettings = {
                        openWirelessDebuggingSettings(context)
                    }
                )
            }

            // System Permissions Section (No accessibility)
            item(key = "permissions_card") {
                PermissionStatusSection(
                    hasOverlayPermission = hasOverlayPermission,
                    isBatteryIgnoring = isBatteryIgnoring,
                    onRequestOverlay = { requestOverlayPermission(context) },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRequestBatteryExemption = { requestBatteryOptimizationExemption(context) }
                )
            }

            // Timer Configuration Section (Modified: centered duration, removed label text)
            item(key = "timer_card") {
                TimerSettingsSection(
                    timerEnabled = config.timerEnabled,
                    timerMinutes = config.timerMinutes,
                    isDeviceAdminActive = isDeviceAdminActive,
                    onToggleTimer = { prefsManager.setTimerEnabled(it) },
                    onUpdateMinutes = { prefsManager.setTimerMinutes(it) },
                    onRequestAdmin = { requestDeviceAdminPermission(context) }
                )
            }

            // Wake-Up Mechanisms Section (Modified: Added call incoming wake toggle)
            item(key = "wakeup_card") {
                WakeUpSettingsSection(
                    config = config,
                    onUpdateFourFinger = { prefsManager.setFourFingerWake(it) },
                    onUpdatePowerKey = { prefsManager.setPowerKeyWake(it) },
                    onUpdateEmergencyTap = { prefsManager.setEmergencyTapWake(it) },
                    onUpdateHaptic = { prefsManager.setHapticFeedback(it) },
                    onUpdateCallWake = { prefsManager.setCallWake(it) }
                )
            }

            // Standby & Power Configuration
            item(key = "power_card") {
                StandbyConfigSection(
                    config = config,
                    onUpdateDelay = { prefsManager.setDelaySeconds(it) },
                    onUpdateCpuLock = { prefsManager.setCpuWakeLock(it) },
                    onUpdateOledPureBlack = { prefsManager.setOledPureBlack(it) },
                    onUpdateAutoBoot = { prefsManager.setAutoStartOnBoot(it) }
                )
            }

            item(key = "bottom_space") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MasterStatusHeroCard(
    isStandbyActive: Boolean,
    hasOverlayPermission: Boolean,
    isAdbPaired: Boolean,
    onToggleStandby: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isStandbyActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isStandbyActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !hasOverlayPermission -> "需要悬浮窗权限"
                        isStandbyActive -> "息屏挂机中 · 状态栏已隐藏"
                        isAdbPaired -> "无线 ADB 已就绪 · 待命"
                        else -> "待命 (建议先配对无线调试)"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isStandbyActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isStandbyActive) "息屏挂机中" else "一键开启息屏",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onToggleStandby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("toggle_standby_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStandbyActive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (isStandbyActive) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isStandbyActive) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !hasOverlayPermission -> "前往授予悬浮窗权限"
                        isStandbyActive -> "退出息屏挂机"
                        else -> "立即开启息屏挂机"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun WirelessAdbSection(
    isPaired: Boolean,
    isConnected: Boolean,
    isSearching: Boolean,
    discoveredPairingPort: Int?,
    statusMessage: String,
    savedConnectPort: Int,
    onStartPairingSearch: () -> Unit,
    onStopPairingSearch: () -> Unit,
    onSubmitPairCode: suspend (port: Int, code: String) -> Unit,
    onAutoAuthorize: suspend () -> Unit,
    onManualConnect: suspend (port: Int) -> Unit,
    onOpenWirelessSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var inputCode by remember { mutableStateOf("") }
    val defaultPort = if (savedConnectPort > 0) savedConnectPort.toString() else "5555"
    var customConnectPort by remember(savedConnectPort) { mutableStateOf(defaultPort) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wireless_adb_section_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "无线调试 ADB 权限",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Status Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isConnected) MaterialTheme.colorScheme.primaryContainer
                            else if (isPaired) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            isConnected -> "已连接"
                            isPaired -> "已配对"
                            else -> "未授权"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = when {
                                isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                                isPaired -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Command Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "adb shell cmd statusbar collapse",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Status message
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "状态：$statusMessage",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Action Buttons: Auto-Authorize & Direct Jump to Wireless Debugging Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            onAutoAuthorize()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("auto_authorize_adb_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "一键获取授权",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                FilledTonalButton(
                    onClick = onOpenWirelessSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("open_wireless_debugging_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "一键直达无线调试",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Step instructions (Persistent key explanation)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "半永久授权指引：",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "1. 软件已在本地安全存储持久化 Key，配对一次即可半永久使用！\n" +
                            "2. 点击「一键直达无线调试」开启无线调试 -> 点击「使用配对码配对设备」\n" +
                            "3. 点击下方「开启自动搜索与通知配对」，在通知栏直接回复6位配对码即可\n" +
                            "4. 手机开机或重连Wi-Fi后，软件会自动监听端口并重新获取授权，亦可随时点击「一键获取授权」！",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                )
            }

            // Pairing Search button (For initial setup or re-pairing)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isSearching) {
                    OutlinedButton(
                        onClick = onStartPairingSearch,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("start_adb_pairing_search_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPaired) "重新配对无线调试" else "开启自动搜索与通知配对",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onStopPairingSearch,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("stop_adb_pairing_search_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("搜索端口中… 点击停止")
                    }
                }
            }

            // Inline pairing fallback if port is found
            AnimatedVisibility(visible = discoveredPairingPort != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "已发现配对端口: ${discoveredPairingPort ?: 0} (亦可在此直接输入)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { if (it.length <= 6) inputCode = it },
                            placeholder = { Text("6位配对码", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Button(
                            onClick = {
                                val port = discoveredPairingPort ?: return@Button
                                if (inputCode.isNotBlank()) {
                                    coroutineScope.launch {
                                        onSubmitPairCode(port, inputCode)
                                    }
                                }
                            },
                            enabled = inputCode.length >= 6,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("配对")
                        }
                    }
                }
            }

            // Custom connect port fallback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customConnectPort,
                    onValueChange = { customConnectPort = it },
                    placeholder = { Text("端口(默认5555)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                OutlinedButton(
                    onClick = {
                        val port = customConnectPort.toIntOrNull() ?: 5555
                        coroutineScope.launch {
                            onManualConnect(port)
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("直连端口", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun PermissionStatusSection(
    hasOverlayPermission: Boolean,
    isBatteryIgnoring: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "系统运行权限",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            PermissionItemRow(
                title = "显示在其他应用上层 (悬浮窗)",
                subtitle = "必需权限，用于在游戏上方显示纯黑屏并接管熄屏状态",
                isGranted = hasOverlayPermission,
                actionLabel = "去授权",
                onClick = onRequestOverlay
            )

            PermissionItemRow(
                title = "通知权限 (前台服务与配对回复)",
                subtitle = "用于常驻状态退出挂机，以及在通知栏中直接回复配对码",
                isGranted = true,
                actionLabel = "检查",
                onClick = onRequestNotification
            )

            PermissionItemRow(
                title = "电池不受限制 (后台防杀)",
                subtitle = "不用时符合规范自动进入系统缓存，挂机时不被省电策略杀掉",
                isGranted = isBatteryIgnoring,
                actionLabel = "去设置",
                onClick = onRequestBatteryExemption
            )
        }
    }
}

@Composable
fun PermissionItemRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }

        if (!isGranted) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun TimerSettingsSection(
    timerEnabled: Boolean,
    timerMinutes: Int,
    isDeviceAdminActive: Boolean,
    onToggleTimer: (Boolean) -> Unit,
    onUpdateMinutes: (Int) -> Unit,
    onRequestAdmin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "定时自动锁屏",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = if (timerEnabled) "达到指定时长后锁定手机"
                        else "无限时间息屏挂机，直到手动唤醒",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }
                Switch(
                    checked = timerEnabled,
                    onCheckedChange = onToggleTimer
                )
            }

            AnimatedVisibility(visible = timerEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hours = timerMinutes / 60
                    val minutes = timerMinutes % 60
                    val durationText = if (minutes == 0) "$hours 小时" else "$hours 小时 $minutes 分钟"

                    // Modified per modification 3: Centered duration, deleted "定时挂机时长" label
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "【$durationText (${timerMinutes}分钟)】",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    var sliderPos by remember(timerMinutes) {
                        mutableFloatStateOf(timerMinutes.toFloat())
                    }

                    Slider(
                        value = sliderPos,
                        onValueChange = { newVal ->
                            val snapped = (newVal / 30f).roundToInt() * 30
                            sliderPos = snapped.toFloat().coerceIn(60f, 720f)
                        },
                        onValueChangeFinished = {
                            onUpdateMinutes(sliderPos.toInt())
                        },
                        valueRange = 60f..720f,
                        steps = 21,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 小时", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        Text("刻度: 30分钟", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        Text("12 小时", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }

                    if (!isDeviceAdminActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("系统锁屏管理授权", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
                                Text("授权后定时结束可直接锁定手机屏幕", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                            }
                            OutlinedButton(
                                onClick = onRequestAdmin,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("去授权", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WakeUpSettingsSection(
    config: AppConfig,
    onUpdateFourFinger: (Boolean) -> Unit,
    onUpdatePowerKey: (Boolean) -> Unit,
    onUpdateEmergencyTap: (Boolean) -> Unit,
    onUpdateHaptic: (Boolean) -> Unit,
    onUpdateCallWake: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "唤醒与防误触",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Modified per modification 4: Added call incoming auto wake
            SettingToggleRow(
                icon = Icons.Default.Call,
                title = "通话呼入自动点亮屏幕",
                desc = "通过原生非侵入式 监听系统音频通道，当检测到通话接入时，自动退出息屏并点亮屏幕。",
                checked = config.callWake,
                onCheckedChange = onUpdateCallWake
            )

            SettingToggleRow(
                icon = Icons.Default.TouchApp,
                title = "四指触摸唤醒",
                desc = "息屏期间所有触控完全阻断。任意四根手指同时按在屏幕上即可解锁，防止误触游戏。",
                checked = config.fourFingerWake,
                onCheckedChange = onUpdateFourFinger
            )

            SettingToggleRow(
                icon = Icons.Default.PowerSettingsNew,
                title = "锁屏键 / 电源键开启",
                desc = "按下机身侧边电源/锁屏键时立即退出挂机并恢复屏幕。",
                checked = config.powerKeyWake,
                onCheckedChange = onUpdatePowerKey
            )

            SettingToggleRow(
                icon = Icons.Default.DirectionsRun,
                title = "应急五连击唤醒",
                desc = "单手不便使用四指时，快速连续轻触屏幕 5 次亦可紧急恢复屏幕显示。",
                checked = config.emergencyTapWake,
                onCheckedChange = onUpdateEmergencyTap
            )

            SettingToggleRow(
                icon = Icons.Default.Vibration,
                title = "震动反馈提示",
                desc = "成功唤醒或进入挂机时触发震动确认。",
                checked = config.hapticFeedback,
                onCheckedChange = onUpdateHaptic
            )
        }
    }
}

@Composable
fun StandbyConfigSection(
    config: AppConfig,
    onUpdateDelay: (Int) -> Unit,
    onUpdateCpuLock: (Boolean) -> Unit,
    onUpdateOledPureBlack: (Boolean) -> Unit,
    onUpdateAutoBoot: (Boolean) -> Unit
) {
    val delayOptions = remember { listOf(0 to "即时", 3 to "3秒", 5 to "5秒", 10 to "10秒") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "全屏与电源设置",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Column {
                Text(
                    text = "启动延迟倒计时",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    delayOptions.forEach { (seconds, label) ->
                        val selected = config.delaySeconds == seconds
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateDelay(seconds) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            SettingToggleRow(
                icon = Icons.Default.Visibility,
                title = "OLED 纯黑全屏防烧屏",
                desc = "使用纯黑(#000000)关闭全部像素发光，并将屏幕背光降至最低，隐藏全部状态栏与导航键。",
                checked = config.oledPureBlack,
                onCheckedChange = onUpdateOledPureBlack
            )

            SettingToggleRow(
                icon = Icons.Default.BatteryAlert,
                title = "CPU 挂机防休眠锁 (WakeLock)",
                desc = "挂机期间保持 CPU 运转，防止大型游戏后台被系统暂停或被休眠杀掉。",
                checked = config.cpuWakeLock,
                onCheckedChange = onUpdateCpuLock
            )

            SettingToggleRow(
                icon = Icons.Default.PowerSettingsNew,
                title = "开机自动就绪",
                desc = "设备重启后自动待命，快捷磁贴立即可用。",
                checked = config.autoStartOnBoot,
                onCheckedChange = onUpdateAutoBoot
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun valPowerManager(context: Context): PowerManager {
    return context.getSystemService(Context.POWER_SERVICE) as PowerManager
}

private fun isDeviceAdminEnabled(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, FakeStandbyAdminReceiver::class.java)
    return dpm.isAdminActive(adminComponent)
}

private fun requestOverlayPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }
}

private fun requestBatteryOptimizationExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "请在「设置 - 应用 - FakeStandby - 电池」中选择「无限制」", Toast.LENGTH_LONG).show()
    }
}

private fun requestDeviceAdminPermission(context: Context) {
    try {
        val adminComponent = ComponentName(context, FakeStandbyAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "FakeStandby 需要设备管理器权限以在定时挂机结束时自动锁定手机屏幕。")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开设备管理授权设置", Toast.LENGTH_SHORT).show()
    }
}

private fun openWirelessDebuggingSettings(context: Context) {
    val intents = listOf(
        // Direct intent to Wireless Debugging settings (Android 11+)
        Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
        // Component intent for Samsung One UI & stock Android SubSettings
        Intent().apply {
            component = ComponentName("com.android.settings", "com.android.settings.SubSettings")
            putExtra(":settings:show_fragment", "com.android.settings.development.WirelessDebuggingFragment")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
        // Developer settings fallback
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
        // General settings fallback
        Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )

    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
    Toast.makeText(context, "无法自动打开，请手动前往系统设置 -> 开发者选项 -> 无线调试", Toast.LENGTH_LONG).show()
}

