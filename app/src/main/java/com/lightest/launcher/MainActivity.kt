package com.lightest.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.os.Build
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.core.view.WindowCompat
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.lightest.launcher.data.LauncherRepository
import com.lightest.launcher.model.AppItem
import com.lightest.launcher.ui.AppDetailDialog
import com.lightest.launcher.ui.rememberBatteryState
import com.lightest.launcher.ui.rememberTimeAndDate
import com.lightest.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }

        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT

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
    // Use a scope tied to this composable's lifecycle so coroutines are cancelled on dispose
    val scope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var selectedAppForDialog by remember { mutableStateOf<AppItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Edit Mode State
    var isEditMode by remember { mutableStateOf(false) }
    var selectedAppToSwap by remember { mutableStateOf<AppItem?>(null) }

    // Initial app load on IO thread
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = LauncherRepository.getInstalledApps(context)
            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoading = false
            }
        }
    }

    // Package install/remove listener — uses rememberCoroutineScope so it is bound to
    // this composable's lifetime and is NOT leaked like a raw CoroutineScope().
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart ?: return
                when (intent.action) {
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        // Incremental: filter in-place on Main thread — zero IO, zero reload
                        installedApps = installedApps.filter { it.packageName != pkg }
                    }
                    else -> {
                        // For installs/updates, reload full list on IO thread
                        scope.launch(Dispatchers.IO) {
                            val apps = LauncherRepository.getInstalledApps(ctx)
                            withContext(Dispatchers.Main) { installedApps = apps }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // queryLower is computed once per searchQuery change — NOT on every recomposition.
    // Previously this was outside remember, causing a new String allocation every clock tick.
    val filteredApps = remember(searchQuery, installedApps) {
        val queryLower = searchQuery.lowercase()
        if (queryLower.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.labelLower.contains(queryLower) ||
                        it.packageName.contains(queryLower)
            }
        }
    }

    val timeData by rememberTimeAndDate()
    val batteryData by rememberBatteryState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                    text = if (selectedAppToSwap == null)
                        "Tap an app to select, then tap another to swap!"
                    else
                        "Tap another app to swap with ${selectedAppToSwap!!.label}!",
                    color = Color(0xFF34C759),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Collapsible Search Bar
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
                // rememberLazyGridState persists scroll position across recompositions
                val gridState = rememberLazyGridState()
                val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 64.dp + navBarPadding),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // key= lets Compose efficiently diff the list on app install/uninstall.
                    // contentType= enables Compose to reuse composition slots across items.
                    items(
                        items = filteredApps,
                        key = { app -> app.stableKey },   // pre-computed, zero allocation
                        contentType = { "app_card" }
                    ) { app ->
                        AppCard(
                            app = app,
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
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }

        // App Detail Dialog (Long Press)
        selectedAppForDialog?.let { app ->
            AppDetailDialog(
                appItem = app,
                onDismiss = { selectedAppForDialog = null },
                onOpenApp = {
                    launchApp(context, app)
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

        // Dynamic battery bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            val batteryColor = when {
                batteryPct <= 20 -> Color(0xFFFF3B30) // Red
                batteryPct <= 50 -> Color(0xFFFF9500) // Orange
                else             -> Color(0xFF34C759) // Green
            }

            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(4.dp)
                    .background(Color(0xFF333333))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (batteryPct / 100f).coerceIn(0.02f, 1f))
                        .background(batteryColor)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "$batteryPct%",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
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

            Spacer(modifier = Modifier.width(16.dp))

            TextButton(onClick = onToggleEditMode) {
                Text(
                    text = if (isEditMode) "Done" else "Edit Layout",
                    color = if (isEditMode) Color(0xFF34C759) else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: AppItem,
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
                    if (isHighlighted) Color(0xFF34C759) else Color.Black,
                    shape = CircleShape
                )
                .border(
                    width = if (isHighlighted) 2.dp else 0.dp,
                    color = if (isHighlighted) Color.White else Color.Transparent,
                    shape = CircleShape
                )
                .clip(CircleShape)
        ) {
            Image(
                bitmap = app.iconBitmap,
                contentDescription = app.label,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    // graphicsLayer scales on the GPU — no layout/measure pass, zero CPU cost
                    .then(
                        if (app.isAdaptiveIcon) Modifier.graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
                        else Modifier
                    )
            )
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