package com.lightest.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsPrefs {
    private const val PREFS_NAME = "launcher_settings"
    private const val KEY_SHOW_WORK_APPS = "show_work_apps"
    private const val KEY_SHARP_EDGES = "sharp_edges"
    private const val KEY_WHITE_THEME = "white_theme"
    private const val KEY_CALENDAR_MONTHS = "calendar_months"
    private const val KEY_HIDDEN_APPS = "hidden_apps"

    private lateinit var prefs: SharedPreferences

    private val _hudSettingsFlow = MutableStateFlow(HudSettings())
    val hudSettingsFlow: StateFlow<HudSettings> = _hudSettingsFlow.asStateFlow()

    private val _showWorkAppsFlow = MutableStateFlow(true)
    val showWorkAppsFlow: StateFlow<Boolean> = _showWorkAppsFlow.asStateFlow()

    private val _sharpEdgesFlow = MutableStateFlow(false)
    val sharpEdgesFlow: StateFlow<Boolean> = _sharpEdgesFlow.asStateFlow()

    private val _whiteThemeFlow = MutableStateFlow(false)
    val whiteThemeFlow: StateFlow<Boolean> = _whiteThemeFlow.asStateFlow()

    // 0 = off, 1 = current month, 2 = current+next, 3 = current+next+next-next
    private val _calendarMonthsFlow = MutableStateFlow(0)
    val calendarMonthsFlow: StateFlow<Int> = _calendarMonthsFlow.asStateFlow()

    private val _hiddenAppsFlow = MutableStateFlow<Set<String>>(emptySet())
    val hiddenAppsFlow: StateFlow<Set<String>> = _hiddenAppsFlow.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _showWorkAppsFlow.value = prefs.getBoolean(KEY_SHOW_WORK_APPS, true)
        _sharpEdgesFlow.value = prefs.getBoolean(KEY_SHARP_EDGES, false)
        _whiteThemeFlow.value = prefs.getBoolean(KEY_WHITE_THEME, false)
        _calendarMonthsFlow.value = prefs.getInt(KEY_CALENDAR_MONTHS, 0)
        _hiddenAppsFlow.value = prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()

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
            showRingVolume = prefs.getBoolean("show_ring_volume", true),
            // Existing installs inherit Battery % so the HUD does not suddenly change.
            showHealth = prefs.getBoolean(
                "show_health",
                prefs.getBoolean("show_battery_percent", true)
            ),
            showCurrent = prefs.getBoolean(
                "show_current",
                prefs.getBoolean("show_battery_percent", true)
            )
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
            putBoolean("show_health", settings.showHealth)
            putBoolean("show_current", settings.showCurrent)
        }.apply()
        _hudSettingsFlow.value = settings
    }

    fun setShowWorkApps(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WORK_APPS, show).apply()
        _showWorkAppsFlow.value = show
    }

    fun setSharpEdges(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHARP_EDGES, enabled).apply()
        _sharpEdgesFlow.value = enabled
    }

    fun setWhiteTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WHITE_THEME, enabled).apply()
        _whiteThemeFlow.value = enabled
    }

    fun setCalendarMonths(months: Int) {
        prefs.edit().putInt(KEY_CALENDAR_MONTHS, months).apply()
        _calendarMonthsFlow.value = months
    }

    /** Hide an app from the launcher by its stableKey. Persisted immediately. */
    fun hideApp(stableKey: String) {
        val updated = _hiddenAppsFlow.value + stableKey
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, updated).apply()
        _hiddenAppsFlow.value = updated
    }

    /** Restore a previously hidden app. Persisted immediately. */
    fun unhideApp(stableKey: String) {
        val updated = _hiddenAppsFlow.value - stableKey
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, updated).apply()
        _hiddenAppsFlow.value = updated
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
    val showRingVolume: Boolean = true,
    val showHealth: Boolean = true,
    val showCurrent: Boolean = true
)
