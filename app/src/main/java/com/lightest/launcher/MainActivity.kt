package com.lightest.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lightest.launcher.data.IconCache
import com.lightest.launcher.data.SettingsPrefs
import com.lightest.launcher.data.HudSettings
import com.lightest.launcher.data.LauncherRepository
import com.lightest.launcher.model.AppItem
import com.lightest.launcher.model.IconEntry
import com.lightest.launcher.ui.AppDetailDialog
import com.lightest.launcher.ui.SettingsScreen
import com.lightest.launcher.ui.rememberBatteryState
import com.lightest.launcher.ui.rememberSystemStats
import com.lightest.launcher.ui.rememberTimeAndDate
import com.lightest.launcher.ui.rememberVolumeState
import com.lightest.launcher.ui.theme.LauncherTheme
import com.lightest.launcher.ui.theme.LauncherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsPrefs.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }

        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.BLACK

        setContent {
            LauncherTheme {
                PitchBlackLauncherScreen()
            }
        }
    }
}

@Composable
fun PitchBlackLauncherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    val iconMap: SnapshotStateMap<String, IconEntry> = remember { mutableStateOf<SnapshotStateMap<String, IconEntry>>(mutableStateMapOf()) }.value
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var selectedAppForDialog by remember { mutableStateOf<AppItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedAppToSwap by remember { mutableStateOf<AppItem?>(null) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val iconLoadJob = remember { arrayOfNulls<Job>(1) }

    fun startIconLoad(apps: List<AppItem>) {
        iconLoadJob[0]?.cancel()
        iconLoadJob[0] = scope.launch {
            LauncherRepository.loadIcons(context, apps) { key, entry ->
                iconMap[key] = entry
            }
        }
    }

    fun reloadAll() {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                LauncherRepository.getInstalledApps(context)
            }
            installedApps = apps
            isLoading = false
            val valid = apps.mapTo(HashSet(apps.size)) { it.stableKey }
            val stale = iconMap.keys.filter { it !in valid }
            stale.forEach { iconMap.remove(it) }
            startIconLoad(apps)
        }
    }

    // Intercept back presses so Android never re-delivers the HOME intent
    // (which causes the launcher slide-in animation bug on spam-back).
    BackHandler(enabled = true) {
        when {
            isSettingsOpen -> isSettingsOpen = false
            isSearchVisible || searchQuery.isNotEmpty() -> {
                isSearchVisible = false
                searchQuery = ""
            }
            isEditMode -> {
                isEditMode = false
                selectedAppToSwap = null
            }
            // On idle home screen: refresh the app list and HUD stats
            else -> {
                reloadAll()
                refreshKey++
            }
        }
    }

    LaunchedEffect(Unit) {
        val apps = withContext(Dispatchers.IO) {
            LauncherRepository.getInstalledApps(context)
        }
        installedApps = apps
        isLoading = false
        startIconLoad(apps)
    }

    DisposableEffect(Unit) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val callback = object : android.content.pm.LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
                reloadAll()
            }
            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
                reloadAll()
            }
            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
                reloadAll()
            }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
                reloadAll()
            }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
                if (!replacing) reloadAll()
            }
            override fun onPackagesSuspended(packageNames: Array<out String>, user: android.os.UserHandle) {
                reloadAll()
            }
            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: android.os.UserHandle) {
                reloadAll()
            }
        }
        launcherApps.registerCallback(callback)
        onDispose {
            iconLoadJob[0]?.cancel()
            launcherApps.unregisterCallback(callback)
        }
    }

    val showWorkApps by SettingsPrefs.showWorkAppsFlow.collectAsState(initial = true)
    val hiddenApps by SettingsPrefs.hiddenAppsFlow.collectAsState(initial = emptySet())

    val (regularApps, workApps) = remember(searchQuery, installedApps, showWorkApps, hiddenApps) {
        val nonHidden = installedApps.filter { it.stableKey !in hiddenApps }
        val filtered = LauncherRepository.filterApps(nonHidden, searchQuery)
        val regular = filtered.filter { !it.isWorkProfile }
        val work = filtered.filter { it.isWorkProfile }
        regular to work
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount < -15) { 
                            isSearchVisible = true
                        } else if (dragAmount > 15) {
                            isSearchVisible = false
                            searchQuery = ""
                        }
                    }
                )
            }
    ) {
        if (isSettingsOpen) {
            SettingsScreen(
                onDismiss = { isSettingsOpen = false },
                allApps = installedApps
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            CenteredMinimalHeader(
                isSearchOpen = isSearchVisible,
                onToggleSearch = { isSearchVisible = !isSearchVisible },
                isEditMode = isEditMode,
                onToggleEditMode = {
                    isEditMode = !isEditMode
                    selectedAppToSwap = null
                    isSearchVisible = false
                },
                refreshKey = refreshKey
            )

            AnimatedVisibility(visible = isEditMode) {
                Text(
                    text = if (selectedAppToSwap == null) {
                        "Tap an app to select, then tap another to swap!"
                    } else {
                        "Tap another app to swap with ${selectedAppToSwap!!.label}!"
                    },
                    color = LauncherColors.Green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(
                visible = isSearchVisible || searchQuery.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            val totalSize = regularApps.size + if (showWorkApps) workApps.size else 0
                            Text(
                                text = "Search $totalSize apps...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Text(
                                        text = "✕",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading apps...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            } else {
                val pages = if (showWorkApps && workApps.isNotEmpty()) 2 else 1
                val pagerState = rememberPagerState(pageCount = { pages })

                val handleAppClick: (AppItem) -> Unit = { it ->
                    if (isEditMode) {
                        if (selectedAppToSwap == null) {
                            selectedAppToSwap = it
                        } else {
                            val mutableList = installedApps.toMutableList()
                            val idx1 = mutableList.indexOf(selectedAppToSwap)
                            val idx2 = mutableList.indexOf(it)
                            if (idx1 != -1 && idx2 != -1) {
                                val temp = mutableList[idx1]
                                mutableList[idx1] = mutableList[idx2]
                                mutableList[idx2] = temp
                                installedApps = mutableList
                                scope.launch(Dispatchers.IO) {
                                    LauncherRepository.saveAppOrder(context, mutableList)
                                }
                            }
                            selectedAppToSwap = null
                        }
                    } else {
                        if (it.packageName == context.packageName) {
                            isSettingsOpen = true
                        } else {
                            launchApp(context, it)
                        }
                        isSearchVisible = false
                        searchQuery = ""
                    }
                }

                val handleAppLongClick: (AppItem) -> Unit = { it ->
                    if (!isEditMode) selectedAppForDialog = it
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f)
                    ) { page ->
                        if (page == 0) {
                            AppGrid(
                                apps = regularApps,
                                iconMap = iconMap,
                                isHighlighted = { it.stableKey == selectedAppToSwap?.stableKey },
                                onAppClick = handleAppClick,
                                onAppLongClick = handleAppLongClick
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "Work",
                                    color = LauncherColors.Green,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .align(Alignment.CenterHorizontally)
                                )
                                AppGrid(
                                    apps = workApps,
                                    iconMap = iconMap,
                                    isHighlighted = { it.stableKey == selectedAppToSwap?.stableKey },
                                    onAppClick = handleAppClick,
                                    onAppLongClick = handleAppLongClick
                                )
                            }
                        }
                    }

                    if (pages > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(pages) { iteration ->
                                val color = if (pagerState.currentPage == iteration) LauncherColors.Green else Color.DarkGray
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedAppForDialog?.let { app ->
            AppDetailDialog(
                appItem = app,
                iconEntry = iconMap[app.stableKey],
                onDismiss = { selectedAppForDialog = null },
                onHideApp = {
                    SettingsPrefs.hideApp(app.stableKey)
                    selectedAppForDialog = null
                },
                onEditLayout = {
                    isEditMode = true
                    selectedAppToSwap = app
                    isSearchVisible = false
                    searchQuery = ""
                }
            )
        }
    }
}

@Composable
fun AppGrid(
    apps: List<AppItem>,
    iconMap: Map<String, IconEntry>,
    isHighlighted: (AppItem) -> Boolean,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 72.dp),
        state = gridState,
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = apps,
            key = { app -> app.stableKey },
            contentType = { "app_card" }
        ) { app ->
            val iconEntry = iconMap[app.stableKey]
            AppCard(
                app = app,
                iconEntry = iconEntry,
                isHighlighted = isHighlighted(app),
                onAppClick = { onAppClick(app) },
                onAppLongClick = { onAppLongClick(app) }
            )
        }
        item(key = "bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CenteredMinimalHeader(
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    refreshKey: Int = 0
) {
    val hud by SettingsPrefs.hudSettingsFlow.collectAsState(initial = HudSettings())

    val timeData by rememberTimeAndDate(refreshKey)
    val batteryData by rememberBatteryState(refreshKey)
    val volumeData by rememberVolumeState(refreshKey)
    val systemStats by rememberSystemStats(refreshKey)

    val time12 = timeData.time12
    val amPm = timeData.amPm
    val dateText = timeData.dateText
    val weekday = timeData.weekday
    val uptimeText = timeData.uptimeText
    val batteryPct = batteryData.percentage
    val isCharging = batteryData.isCharging
    val deviceTempCelsius = batteryData.deviceTemperatureCelsius
    val batteryTempCelsius = batteryData.batteryTemperatureCelsius
    val voltageMv = batteryData.voltageMv
    val healthLabel = batteryData.healthLabel
    val currentMa = batteryData.currentMa
    val thermalLabel = batteryData.thermalLabel
    val memoryUsedTotal = systemStats.memoryUsedTotalLabel
    val storageUsedTotal = systemStats.storageUsedTotalLabel
    val freeRamGb = systemStats.freeRamGb
    val totalRamGb = systemStats.totalRamGb
    val freeStorageGb = systemStats.freeStorageGb
    val totalStorageGb = systemStats.totalStorageGb
    val refreshRateHz = systemStats.refreshRateHz
    val mediaVolumePct = volumeData.mediaVolumePercentage
    val ringVolumePct = volumeData.ringVolumePercentage
    val ringerMode = volumeData.ringerMode

    val muted = Color.White.copy(alpha = 0.78f)

    // —— Status colors (green = good, orange = caution, red = bad) ——
    val voltageV = voltageMv / 1000f
    val voltageColor = when {
        voltageMv <= 0 -> muted
        voltageV < 3.50f -> LauncherColors.Red
        voltageV < 3.60f -> LauncherColors.Orange
        voltageV > 4.30f -> LauncherColors.Red
        voltageV > 4.20f -> LauncherColors.Orange
        else -> LauncherColors.Green
    }
    val healthColor = when (healthLabel) {
        "Good" -> LauncherColors.Green
        "Cold" -> LauncherColors.Orange
        "Unknown" -> muted
        else -> LauncherColors.Red
    }
    val thermalColor = when (thermalLabel) {
        "Normal" -> LauncherColors.Green
        "Warm" -> LauncherColors.Orange
        else -> LauncherColors.Red
    }
    val deviceTemp = deviceTempCelsius
    val deviceColor = when {
        deviceTemp == null -> muted
        deviceTemp >= 45f -> LauncherColors.Red
        deviceTemp >= 40f -> LauncherColors.Orange
        else -> LauncherColors.Green
    }
    val batteryTempColor = when {
        batteryTempCelsius >= 40f -> LauncherColors.Red
        batteryTempCelsius >= 35f -> LauncherColors.Orange
        else -> LauncherColors.Green
    }
    val batteryPctColor = when {
        batteryPct <= 15 -> LauncherColors.Red
        batteryPct <= 30 -> LauncherColors.Orange
        else -> LauncherColors.Green
    }
    // Memory: based on free ratio remaining
    val freeRamRatio = if (totalRamGb > 0f) freeRamGb / totalRamGb else 1f
    val memoryColor = when {
        freeRamRatio < 0.12f -> LauncherColors.Red
        freeRamRatio < 0.25f -> LauncherColors.Orange
        else -> LauncherColors.Green
    }
    val freeStorageRatio = if (totalStorageGb > 0f) freeStorageGb / totalStorageGb else 1f
    val storageColor = when {
        freeStorageRatio < 0.10f -> LauncherColors.Red
        freeStorageRatio < 0.20f -> LauncherColors.Orange
        else -> LauncherColors.Green
    }
    val currentColor = when {
        currentMa == null -> muted
        isCharging && kotlin.math.abs(currentMa) >= 1500 -> LauncherColors.Green
        isCharging -> LauncherColors.Orange
        else -> muted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (hud.showTime || hud.showDate) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hud.showTime) {
                    Text(
                        text = time12,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = amPm,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (hud.showTime && hud.showDate) {
                    Text(
                        text = "  ·  ",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (hud.showDate) {
                    Text(
                        text = "$weekday, $dateText",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (hud.showBatteryBar || hud.showBatteryPercent) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hud.showBatteryBar) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(8.dp)
                            .background(LauncherColors.TrackBackground, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (batteryPct / 100f).coerceIn(0.02f, 1f))
                                .background(
                                    if (isCharging) LauncherColors.Green else batteryPctColor,
                                    RoundedCornerShape(2.dp)
                                )
                                .align(Alignment.CenterStart)
                        )
                        if (isCharging) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(6.dp)) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width * 0.6f, 0f)
                                    lineTo(0f, size.height * 0.55f)
                                    lineTo(size.width * 0.45f, size.height * 0.55f)
                                    lineTo(size.width * 0.2f, size.height)
                                    lineTo(size.width, size.height * 0.45f)
                                    lineTo(size.width * 0.55f, size.height * 0.45f)
                                    close()
                                }
                                drawPath(path, Color.Black)
                            }
                        }
                    }
                }
                if (hud.showBatteryPercent) {
                    Text(
                        text = "$batteryPct%",
                        color = if (isCharging) LauncherColors.Green else batteryPctColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = if (hud.showBatteryBar) 8.dp else 0.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Metrics grid — "Label : value" with per-item status colors
        val showAnyMetric = hud.showDeviceTemp || hud.showBatteryTemp || hud.showVoltage || hud.showThermal || hud.showMemory || hud.showStorage || hud.showRingVolume || hud.showMediaVolume || hud.showRefreshRate || hud.showUptime
        if (showAnyMetric) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (hud.showDeviceTemp && deviceTemp != null) {
                HudMetric(
                    label = "Device",
                    value = "${"%.1f".format(deviceTemp)}°C",
                    color = deviceColor,
                    emphasize = deviceTemp >= 40f
                )
            }
            if (hud.showBatteryTemp) {
                HudMetric(
                    label = "Battery",
                    value = "${"%.1f".format(batteryTempCelsius)}°C",
                    color = batteryTempColor,
                    emphasize = batteryTempCelsius >= 35f
                )
            }
            if (hud.showVoltage && voltageMv > 0) {
                HudMetric(
                    label = "Voltage",
                    value = "${"%.2f".format(voltageV)} V",
                    color = voltageColor,
                    emphasize = voltageColor != LauncherColors.Green
                )
            }
            // Health and current are implicitly hidden unless we want toggles. I'll tie them to BatteryTemp for now, or just leave them out if not requested?
            // The user didn't ask for them specifically. Let's hide them if BatteryTemp is hidden to save space, or just leave them since they aren't toggled.
            // Wait, they asked for "disable any thing seprately". Let's wrap them in hud.showBatteryTemp or showBatteryPercent to save clutter.
            
            if (hud.showBatteryPercent) {
                HudMetric(
                    label = "Health",
                    value = healthLabel,
                    color = healthColor,
                    emphasize = healthColor == LauncherColors.Red
                )
                if (currentMa != null) {
                    HudMetric(
                        label = if (isCharging) "Charging" else "Current",
                        value = if (isCharging) {
                            "${kotlin.math.abs(currentMa)} mA"
                        } else {
                            "$currentMa mA"
                        },
                        color = currentColor,
                        emphasize = isCharging
                    )
                }
            }

            if (hud.showThermal) {
                HudMetric(
                    label = "Thermal",
                    value = thermalLabel,
                    color = thermalColor,
                    emphasize = thermalLabel != "Normal"
                )
            }
            if (hud.showMemory) {
                HudMetric(
                    label = "Memory",
                    value = "$memoryUsedTotal GB",
                    color = memoryColor,
                    emphasize = memoryColor != LauncherColors.Green
                )
            }
            if (hud.showStorage) {
                HudMetric(
                    label = "Storage",
                    value = "$storageUsedTotal GB",
                    color = storageColor,
                    emphasize = storageColor != LauncherColors.Green
                )
            }
            
            // Refresh and uptime
            if (hud.showRefreshRate) {
                HudMetric(
                    label = "Refresh",
                    value = "$refreshRateHz Hz",
                    color = muted
                )
            }
            if (hud.showUptime) {
                HudMetric(
                    label = "Uptime",
                    value = uptimeText,
                    color = muted
                )
            }

            if (hud.showRingVolume) {
                val ringerText = when (ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> "Silent"
                    AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                    else -> "$ringVolumePct%"
                }
                HudMetric(label = "Ring", value = ringerText, color = muted)
            }
            if (hud.showMediaVolume) {
                HudMetric(label = "Media", value = "$mediaVolumePct%", color = muted)
            }
        }
        } // close if (showAnyMetric)

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = "Search",
                    tint = if (isSearchOpen) LauncherColors.Green else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isEditMode) {
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = onToggleEditMode) {
                    Text(
                        text = "Done",
                        color = LauncherColors.Green,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Single HUD chip: "Label : value" with status color. */
@Composable
private fun HudMetric(
    label: String,
    value: String,
    color: Color,
    emphasize: Boolean = false
) {
    Text(
        text = "$label : $value",
        color = color,
        fontSize = 10.sp,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: AppItem,
    iconEntry: IconEntry?,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isHighlighted) LauncherColors.Green else LauncherColors.CardBackground,
                    shape = CircleShape
                )
                .border(
                    width = if (isHighlighted) 2.dp else 0.dp,
                    color = if (isHighlighted) Color.White else Color.Transparent,
                    shape = CircleShape
                )
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (iconEntry != null) {
                Image(
                    bitmap = iconEntry.bitmap,
                    contentDescription = app.label,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (iconEntry.isAdaptive) {
                                Modifier.graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
                            } else {
                                Modifier
                            }
                        )
                )
            }
            // Empty dark circle placeholder while icon streams in — no spinner work.
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (app.isWorkProfile) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_work),
                    contentDescription = "Work Profile",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

private fun launchApp(context: Context, app: AppItem) {
    try {
        if (app.userHandle != null) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
                as android.content.pm.LauncherApps
            val component = ComponentName(app.packageName, app.className)
            launcherApps.startMainActivity(component, app.userHandle, null, null)
        } else {
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.className)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(launchIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error launching app: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
