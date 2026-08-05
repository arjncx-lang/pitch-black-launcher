package com.lightest.launcher.ui

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class SystemStats(
    /** Used RAM in GiB. */
    val usedRamGb: Float = 0f,
    /** Total RAM in GiB. */
    val totalRamGb: Float = 0f,
    /** Used internal storage in GiB. */
    val usedStorageGb: Float = 0f,
    /** Total internal storage in GiB. */
    val totalStorageGb: Float = 0f,
    /** Active display refresh rate, rounded Hz. */
    val refreshRateHz: Int = 60
) {
    val freeRamGb: Float get() = max(0f, totalRamGb - usedRamGb)
    val freeStorageGb: Float get() = max(0f, totalStorageGb - usedStorageGb)

    /** e.g. "5/8" */
    val memoryUsedTotalLabel: String
        get() = formatUsedTotal(usedRamGb, totalRamGb)

    /** e.g. "24/128" */
    val storageUsedTotalLabel: String
        get() = formatUsedTotal(usedStorageGb, totalStorageGb)
}

/** Compact used/total like "5/8" or "24/128". */
internal fun formatUsedTotal(usedGb: Float, totalGb: Float): String {
    val total = totalGb.coerceAtLeast(0.1f)
    val used = usedGb.coerceIn(0f, total)
    // Snap total to common marketed capacities so labels read cleanly (8 GB, 128 GB).
    val t = snapCapacityGb(total)
    val u = used.roundToInt().coerceIn(0, t)
    return "$u/$t"
}

/** Map measured GiB to the nearest common marketed size. */
private fun snapCapacityGb(measuredGb: Float): Int {
    val buckets = intArrayOf(2, 3, 4, 6, 8, 12, 16, 18, 24, 32, 64, 128, 256, 512, 1024)
    return buckets.minByOrNull { kotlin.math.abs(it - measuredGb) }
        ?.takeIf { kotlin.math.abs(it - measuredGb) <= measuredGb * 0.25f + 1f }
        ?: measuredGb.roundToInt().coerceAtLeast(1)
}

/**
 * Passive system stats: RAM, storage, refresh rate.
 *
 * No timers of our own. Refreshes on:
 *  - first composition
 *  - [Intent.ACTION_TIME_TICK] (once/minute, OS-driven)
 *  - display add/change/remove (refresh rate)
 */
@Composable
fun rememberSystemStats(refreshKey: Int = 0): State<SystemStats> {
    val context = LocalContext.current
    val statsState = remember { mutableStateOf(SystemStats()) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context, refreshKey) {
        fun refresh() {
            scope.launch(Dispatchers.IO) {
                val next = readSystemStats(context)
                withContext(Dispatchers.Main) {
                    if (next != statsState.value) {
                        statsState.value = next
                    }
                }
            }
        }
        
        refresh()

        val tickReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
            addAction(Intent.ACTION_DEVICE_STORAGE_OK)
        }
        context.registerReceiver(tickReceiver, filter)

        val displayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = refresh()
            override fun onDisplayRemoved(displayId: Int) = refresh()
            override fun onDisplayChanged(displayId: Int) = refresh()
        }
        displayManager?.registerDisplayListener(
            displayListener,
            Handler(Looper.getMainLooper())
        )

        onDispose {
            try {
                context.unregisterReceiver(tickReceiver)
            } catch (_: Exception) { }
            try {
                displayManager?.unregisterDisplayListener(displayListener)
            } catch (_: Exception) { }
        }
    }

    return statsState
}

private val cachedMemoryInfo = ActivityManager.MemoryInfo()

private fun readSystemStats(context: Context): SystemStats {
    val (usedRam, totalRam) = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(cachedMemoryInfo)
        val total = cachedMemoryInfo.totalMem.toFloat() / (1024f * 1024f * 1024f)
        val free = cachedMemoryInfo.availMem.toFloat() / (1024f * 1024f * 1024f)
        (total - free).coerceAtLeast(0f) to total
    } catch (_: Throwable) {
        0f to 0f
    }

    val (usedStorage, totalStorage) = try {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val total = (stat.blockCountLong * stat.blockSizeLong).toFloat() / (1024f * 1024f * 1024f)
        val free = (stat.availableBlocksLong * stat.blockSizeLong).toFloat() / (1024f * 1024f * 1024f)
        (total - free).coerceAtLeast(0f) to total
    } catch (_: Throwable) {
        0f to 0f
    }

    val hz = try {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        val rate = display?.refreshRate ?: 60f
        rate.toInt().coerceIn(1, 240)
    } catch (_: Throwable) {
        60
    }

    return SystemStats(
        usedRamGb = usedRam,
        totalRamGb = totalRam,
        usedStorageGb = usedStorage,
        totalStorageGb = totalStorage,
        refreshRateHz = hz
    )
}
