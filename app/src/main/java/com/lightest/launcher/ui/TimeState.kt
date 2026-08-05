package com.lightest.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@Immutable
data class TimeData(
    val time12: String = "12:00",
    val amPm: String = "AM",
    val dateText: String = "",
    val weekday: String = "",
    /** Compact uptime since boot, e.g. "2d 4h" or "3h 12m". */
    val uptimeText: String = "0m"
)

@Composable
fun rememberTimeAndDate(refreshKey: Int = 0): State<TimeData> {
    val context = LocalContext.current
    val timeState = remember { mutableStateOf(getTimeData()) }

    DisposableEffect(context, refreshKey) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                timeState.value = getTimeData()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) { }
        }
    }

    return timeState
}

private var cachedLocale: Locale? = null
private var time12Format: SimpleDateFormat? = null
private var amPmFormat: SimpleDateFormat? = null
private var dateTextFormat: SimpleDateFormat? = null
private var weekdayFormat: SimpleDateFormat? = null

/** Allocates formatters per call — safe from any thread, called at most once/minute. */
private fun getTimeData(): TimeData {
    val locale = Locale.getDefault()
    if (locale != cachedLocale || time12Format == null) {
        cachedLocale = locale
        time12Format = SimpleDateFormat("hh:mm", locale)
        amPmFormat = SimpleDateFormat("a", locale)
        dateTextFormat = SimpleDateFormat("MMM d, yyyy", locale)
        weekdayFormat = SimpleDateFormat("EEE", locale)
    }
    
    val now = Calendar.getInstance().time
    return TimeData(
        time12 = time12Format!!.format(now),
        amPm = amPmFormat!!.format(now),
        // Compact date for single-line header.
        dateText = dateTextFormat!!.format(now),
        weekday = weekdayFormat!!.format(now),
        uptimeText = formatUptime(SystemClock.elapsedRealtime())
    )
}

internal fun formatUptime(elapsedMs: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(elapsedMs)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs) % 60
    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
