package com.lightest.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Immutable
data class BatteryData(
    val percentage: Int = 100,
    val isCharging: Boolean = false,
    /** Battery cell temperature — primary signal for battery longevity. */
    val batteryTemperatureCelsius: Float = 0f,
    /**
     * Device (board) temperature — heat from load.
     * Null until the first successful passive read on this device.
     */
    val deviceTemperatureCelsius: Float? = null,
    /** Battery pack voltage in millivolts (from sticky intent). */
    val voltageMv: Int = 0,
    /** Short health label: Good, Hot, Dead, … */
    val healthLabel: String = "—",
    /**
     * Instantaneous current in mA.
     * Positive ≈ charging, negative ≈ discharging (OEM-dependent sign).
     * Null if the device does not report it.
     */
    val currentMa: Int? = null,
    /**
     * System thermal status label (OK / WARM / HOT / …).
     * From PowerManager — free, passive callbacks.
     */
    val thermalLabel: String = "OK"
)

/** Single source of truth for battery intent → data conversion. */
private fun Intent.toBatteryData(
    context: Context,
    deviceTemp: Float?,
    thermalLabel: String
): BatteryData {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val health = getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
    return BatteryData(
        percentage = if (level >= 0 && scale > 0) (level * 100) / scale else 100,
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        batteryTemperatureCelsius = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f,
        deviceTemperatureCelsius = deviceTemp,
        voltageMv = getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
        healthLabel = healthToLabel(health),
        currentMa = readCurrentMa(context),
        thermalLabel = thermalLabel
    )
}

private fun healthToLabel(health: Int): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
    else -> "Unknown"
}

/**
 * BATTERY_PROPERTY_CURRENT_NOW is µA on AOSP; some OEMs report mA.
 * Heuristic: |value| > 10_000 → treat as microamps.
 */
private fun readCurrentMa(context: Context): Int? {
    return try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null
        val raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Long.MIN_VALUE || raw == Long.MAX_VALUE) return null
        val ma = if (kotlin.math.abs(raw) > 10_000L) (raw / 1000L).toInt() else raw.toInt()
        // Ignore absurd readings.
        if (ma in -10_000..10_000) ma else null
    } catch (_: Throwable) {
        null
    }
}

fun thermalStatusToLabel(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "Normal"
    PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
    PowerManager.THERMAL_STATUS_MODERATE -> "Hot"
    PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
    else -> "Normal"
}

/**
 * Passive battery + temperature + health state.
 *
 * Listeners are registered only while the launcher is RESUMED.
 * Device °C is one-shot + 30s cache, forced on Back / resume.
 */
@Composable
fun rememberBatteryState(
    refreshKey: Int = 0,
    enabled: Boolean = true,
    requestDeviceTemp: Boolean = true
): State<BatteryData> {
    val context = LocalContext.current
    val batteryState = remember { mutableStateOf(BatteryData()) }

    val appContext = context.applicationContext
    val powerManager = remember {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    val requestTempHolder = remember { booleanArrayOf(requestDeviceTemp) }
    requestTempHolder[0] = requestDeviceTemp

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) {
                    applyBatteryIntent(
                        appContext, powerManager, batteryState, intent,
                        requestTempHolder[0], forceDeviceTemp = false
                    )
                }
            }
        }
    }

    val thermalListener = remember {
        arrayOfNulls<PowerManager.OnThermalStatusChangedListener>(1)
    }

    fun currentThermalLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || powerManager == null) return "OK"
        return try {
            thermalStatusToLabel(powerManager.currentThermalStatus)
        } catch (_: Throwable) {
            "OK"
        }
    }

    fun refreshNow(forceDeviceTemp: Boolean) {
        val sticky = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            // Peek sticky without registering a second receiver.
            context.registerReceiver(null, filter)
        }
        if (sticky != null) {
            applyBatteryIntent(
                appContext, powerManager, batteryState, sticky,
                requestDeviceTemp, forceDeviceTemp
            )
        } else {
            val thermal = currentThermalLabel()
            val prev = batteryState.value
            if (prev.thermalLabel != thermal) {
                batteryState.value = prev.copy(thermalLabel = thermal)
            }
            if (requestDeviceTemp) {
                DeviceTemperature.request(appContext, force = forceDeviceTemp) { deviceTemp ->
                    val p = batteryState.value
                    if (p.deviceTemperatureCelsius != deviceTemp) {
                        batteryState.value = p.copy(deviceTemperatureCelsius = deviceTemp)
                    }
                }
            }
        }
    }

    HudLifecycleEffect(
        enabled = enabled,
        refreshKey = refreshKey,
        onStart = {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(receiver, filter)
            if (stickyIntent != null) {
                applyBatteryIntent(
                    appContext, powerManager, batteryState, stickyIntent,
                    requestDeviceTemp, forceDeviceTemp = true
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                val listener = PowerManager.OnThermalStatusChangedListener {
                    val thermal = currentThermalLabel()
                    val prev = batteryState.value
                    if (prev.thermalLabel != thermal) {
                        batteryState.value = prev.copy(thermalLabel = thermal)
                    }
                    if (requestDeviceTemp) {
                        DeviceTemperature.request(appContext, force = false) { deviceTemp ->
                            val p = batteryState.value
                            if (p.deviceTemperatureCelsius != deviceTemp) {
                                batteryState.value = p.copy(deviceTemperatureCelsius = deviceTemp)
                            }
                        }
                    }
                }
                thermalListener[0] = listener
                powerManager.addThermalStatusListener(context.mainExecutor, listener)
            }
        },
        onStop = {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) { }
            val listener = thermalListener[0]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && listener != null) {
                try {
                    powerManager?.removeThermalStatusListener(listener)
                } catch (_: Exception) { }
                thermalListener[0] = null
            }
        },
        onRefresh = { refreshNow(forceDeviceTemp = true) }
    )

    return batteryState
}

private fun applyBatteryIntent(
    appContext: Context,
    powerManager: PowerManager?,
    batteryState: androidx.compose.runtime.MutableState<BatteryData>,
    intent: Intent,
    requestDeviceTemp: Boolean,
    forceDeviceTemp: Boolean
) {
    val thermal = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || powerManager == null) {
        "OK"
    } else {
        try {
            thermalStatusToLabel(powerManager.currentThermalStatus)
        } catch (_: Throwable) {
            "OK"
        }
    }
    val pendingDevice = DeviceTemperature.lastCelsius()
    batteryState.value = intent.toBatteryData(appContext, pendingDevice, thermal)

    if (!requestDeviceTemp) return
    DeviceTemperature.request(appContext, force = forceDeviceTemp) { deviceTemp ->
        val prev = batteryState.value
        if (prev.deviceTemperatureCelsius != deviceTemp) {
            batteryState.value = prev.copy(deviceTemperatureCelsius = deviceTemp)
        }
    }
}
