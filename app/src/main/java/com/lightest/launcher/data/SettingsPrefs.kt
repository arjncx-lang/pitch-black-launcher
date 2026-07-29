package com.lightest.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsPrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY_SHOW_SYSTEM_STATS = "show_system_stats"
    private const val KEY_SHOW_WORK_APPS = "show_work_apps"

    private lateinit var prefs: SharedPreferences

    private val _hudSettingsFlow = MutableStateFlow(HudSettings())
    val hudSettingsFlow: StateFlow<HudSettings> = _hudSettingsFlow.asStateFlow()

    private val _showWorkAppsFlow = MutableStateFlow(true)
    val showWorkAppsFlow: StateFlow<Boolean> = _showWorkAppsFlow.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _showWorkAppsFlow.value = prefs.getBoolean(KEY_SHOW_WORK_APPS, true)
        
        _hudSettingsFlow.value = HudSettings(
            showTime = prefs.getBoolean("show_time", true),
            showDate = prefs.getBoolean("show_date", true),
            showBatteryBar = prefs.getBoolean("show_battery_bar", true),
            showBatteryPercent = prefs.getBoolean("show_battery_percent", true),
            showVoltage = prefs.getBoolean("show_voltage", true),
            showDeviceTemp = prefs.getBoolean("show_device_temp", true),
            showBatteryTemp = prefs.getBoolean("show_battery_temp", true),
            showThermal = prefs.getBoolean("show_thermal", true),
            showMemory = prefs.getBoolean("show_memory", true),
            showStorage = prefs.getBoolean("show_storage", true),
            showRefreshRate = prefs.getBoolean("show_refresh", true),
            showUptime = prefs.getBoolean("show_uptime", true),
            showMediaVolume = prefs.getBoolean("show_media_volume", true),
            showRingVolume = prefs.getBoolean("show_ring_volume", true)
        )
    }

    fun updateHudSettings(settings: HudSettings) {
        prefs.edit().apply {
            putBoolean("show_time", settings.showTime)
            putBoolean("show_date", settings.showDate)
            putBoolean("show_battery_bar", settings.showBatteryBar)
            putBoolean("show_battery_percent", settings.showBatteryPercent)
            putBoolean("show_voltage", settings.showVoltage)
            putBoolean("show_device_temp", settings.showDeviceTemp)
            putBoolean("show_battery_temp", settings.showBatteryTemp)
            putBoolean("show_thermal", settings.showThermal)
            putBoolean("show_memory", settings.showMemory)
            putBoolean("show_storage", settings.showStorage)
            putBoolean("show_refresh", settings.showRefreshRate)
            putBoolean("show_uptime", settings.showUptime)
            putBoolean("show_media_volume", settings.showMediaVolume)
            putBoolean("show_ring_volume", settings.showRingVolume)
        }.apply()
        _hudSettingsFlow.value = settings
    }

    fun setShowWorkApps(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WORK_APPS, show).apply()
        _showWorkAppsFlow.value = show
    }
}

data class HudSettings(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val showBatteryBar: Boolean = true,
    val showBatteryPercent: Boolean = true,
    val showVoltage: Boolean = true,
    val showDeviceTemp: Boolean = true,
    val showBatteryTemp: Boolean = true,
    val showThermal: Boolean = true,
    val showMemory: Boolean = true,
    val showStorage: Boolean = true,
    val showRefreshRate: Boolean = true,
    val showUptime: Boolean = true,
    val showMediaVolume: Boolean = true,
    val showRingVolume: Boolean = true
)
