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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import com.lightest.launcher.data.LauncherRepository
import com.lightest.launcher.model.AppItem
import com.lightest.launcher.model.IconEntry
import com.lightest.launcher.ui.AppDetailDialog
import com.lightest.launcher.ui.rememberBatteryState
import com.lightest.launcher.ui.rememberTimeAndDate
import com.lightest.launcher.ui.rememberVolumeState
import com.lightest.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    // Icons live in a SnapshotStateMap so only the card for that key recomposes.
    val iconMap: SnapshotStateMap<String, IconEntry> = remember { mutableStateMapOf() }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var selectedAppForDialog by remember { mutableStateOf<AppItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedAppToSwap by remember { mutableStateOf<AppItem?>(null) }

    // Not Compose state — Job changes must not trigger recomposition.
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
            // Drop icons for apps that no longer exist
            val valid = apps.mapTo(HashSet(apps.size)) { it.stableKey }
            val stale = iconMap.keys.filter { it !in valid }
            stale.forEach { iconMap.remove(it) }
            startIconLoad(apps)
        }
    }

    // Phase 1: paint labels ASAP; Phase 2: stream icons in.
    LaunchedEffect(Unit) {
        val apps = withContext(Dispatchers.IO) {
            LauncherRepository.getInstalledApps(context)
        }
        installedApps = apps
        isLoading = false
        startIconLoad(apps)
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart ?: return
                when (intent.action) {
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                        installedApps = installedApps.filter { it.packageName != pkg }
                        IconCache.removeMemoryByPackage(pkg)
                        iconMap.keys.filter { it.startsWith("$pkg|") }.forEach { iconMap.remove(it) }
                    }
                    else -> reloadAll()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            iconLoadJob[0]?.cancel()
            context.unregisterReceiver(receiver)
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        LauncherRepository.filterApps(installedApps, searchQuery)
    }

    val timeData by rememberTimeAndDate()
    val batteryData by rememberBatteryState()
    val volumeData by rememberVolumeState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            CenteredMinimalHeader(
                time12 = timeData.time12,
                amPm = timeData.amPm,
                dateText = timeData.dateText,
                weekday = timeData.weekday,
                batteryPct = batteryData.percentage,
                isCharging = batteryData.isCharging,
                temperatureCelsius = batteryData.temperatureCelsius,
                mediaVolumePct = volumeData.mediaVolumePercentage,
                ringVolumePct = volumeData.ringVolumePercentage,
                ringerMode = volumeData.ringerMode,
                isSearchOpen = isSearchVisible,
                onToggleSearch = { isSearchVisible = !isSearchVisible },
                isEditMode = isEditMode,
                onToggleEditMode = {
                    isEditMode = !isEditMode
                    selectedAppToSwap = null
                    isSearchVisible = false
                }
            )

            AnimatedVisibility(visible = isEditMode) {
                Text(
                    text = if (selectedAppToSwap == null) {
                        "Tap an app to select, then tap another to swap!"
                    } else {
                        "Tap another app to swap with ${selectedAppToSwap!!.label}!"
                    },
                    color = Color(0xFF34C759),
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
                            Text(
                                text = "Search ${filteredApps.size} apps...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
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
                val gridState = rememberLazyGridState()

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { app -> app.stableKey },
                        contentType = { "app_card" }
                    ) { app ->
                        // Read map here so only this item recomposes when its icon arrives.
                        val iconEntry = iconMap[app.stableKey]
                        AppCard(
                            app = app,
                            iconEntry = iconEntry,
                            isHighlighted = selectedAppToSwap == app,
                            onAppClick = { appItem ->
                                if (isEditMode) {
                                    if (selectedAppToSwap == null) {
                                        selectedAppToSwap = appItem
                                    } else {
                                        val mutableList = installedApps.toMutableList()
                                        val idx1 = mutableList.indexOf(selectedAppToSwap)
                                        val idx2 = mutableList.indexOf(appItem)
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
                                    launchApp(context, appItem)
                                    isSearchVisible = false
                                    searchQuery = ""
                                }
                            },
                            onAppLongClick = {
                                if (!isEditMode) selectedAppForDialog = it
                            }
                        )
                    }
                    item(key = "bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        selectedAppForDialog?.let { app ->
            AppDetailDialog(
                appItem = app,
                iconEntry = iconMap[app.stableKey],
                onDismiss = { selectedAppForDialog = null },
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
fun CenteredMinimalHeader(
    time12: String,
    amPm: String,
    dateText: String,
    weekday: String,
    batteryPct: Int,
    isCharging: Boolean,
    temperatureCelsius: Float,
    mediaVolumePct: Int,
    ringVolumePct: Int,
    ringerMode: Int,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = time12,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = amPm,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Text(
            text = "$weekday, $dateText",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            val tempColor = when {
                temperatureCelsius >= 40f -> Color(0xFFFF3B30)
                temperatureCelsius >= 35f -> Color(0xFFFF9500)
                else -> Color(0xFF34C759)
            }
            Text(
                text = "TMP: ${"%.1f".format(temperatureCelsius)}°C",
                color = tempColor,
                fontSize = 10.sp,
                fontWeight = if (temperatureCelsius >= 40f) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(end = 12.dp)
            )

            val batteryColor = when {
                batteryPct <= 20 -> Color(0xFFFF3B30)
                batteryPct <= 50 -> Color(0xFFFF9500)
                else -> Color(0xFF34C759)
            }

            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(8.dp)
                    .background(Color(0xFF333333), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (batteryPct / 100f).coerceIn(0.02f, 1f))
                        .background(
                            if (isCharging) Color(0xFF34C759) else batteryColor,
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

            Text(
                text = "$batteryPct%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp)
            )

            val ringerText = when (ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "SLT"
                AudioManager.RINGER_MODE_VIBRATE -> "VIB"
                else -> "$ringVolumePct%"
            }
            Text(
                text = "RNG: $ringerText  MED: $mediaVolumePct%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
        }

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
                    tint = if (isSearchOpen) Color(0xFF34C759) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isEditMode) {
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = onToggleEditMode) {
                    Text(
                        text = "Done",
                        color = Color(0xFF34C759),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
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
                    if (isHighlighted) Color(0xFF34C759) else Color(0xFF1A1A1A),
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
