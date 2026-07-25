package com.lightest.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

@Immutable
data class TimeData(
    val time12: String = "12:00",
    val amPm: String = "AM",
    val dateText: String = "",
    val weekday: String = ""
)

// Formatters are created once and reused — no per-second allocation.
private val timeFormat = SimpleDateFormat("hh:mm", Locale.getDefault())
private val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
private val weekdayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

@Composable
fun rememberTimeAndDate(): State<TimeData> {
    val context = LocalContext.current
    val timeState = remember { mutableStateOf(getTimeData()) }

    DisposableEffect(context) {
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
            } catch (e: Exception) {
                // Ignore if unregistering fails
            }
        }
    }

    return timeState
}

private fun getTimeData(): TimeData {
    val now = Calendar.getInstance().time
    return TimeData(
        time12 = timeFormat.format(now),
        amPm = amPmFormat.format(now),
        dateText = dateFormat.format(now),
        weekday = weekdayFormat.format(now)
    )
}
