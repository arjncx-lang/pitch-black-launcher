package com.lightest.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
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
    val timeState = remember { mutableStateOf(getTimeData()) }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val now = cal.time

            val newTime12 = timeFormat.format(now)
            val newAmPm = amPmFormat.format(now)

            // Only recompute date string when minute rolls over (saves allocations every second)
            val currentData = timeState.value
            if (newTime12 != currentData.time12 || newAmPm != currentData.amPm) {
                // Check if date also changed (rare, but correct)
                val newDate = dateFormat.format(now)
                val newWeekday = weekdayFormat.format(now)
                timeState.value = TimeData(
                    time12 = newTime12,
                    amPm = newAmPm,
                    dateText = if (newDate != currentData.dateText) newDate else currentData.dateText,
                    weekday = if (newWeekday != currentData.weekday) newWeekday else currentData.weekday
                )
            }

            // Sleep until the next whole second boundary to minimise wakeups
            val msUntilNextSecond = 1000L - (System.currentTimeMillis() % 1000L)
            delay(msUntilNextSecond)
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
