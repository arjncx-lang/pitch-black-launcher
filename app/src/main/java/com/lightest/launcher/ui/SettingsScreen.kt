package com.lightest.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lightest.launcher.data.SettingsPrefs
import com.lightest.launcher.model.AppItem
import com.lightest.launcher.ui.theme.LauncherColors

@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    allApps: List<AppItem>
) {
    val hudSettings by SettingsPrefs.hudSettingsFlow.collectAsState()
    val showWorkApps by SettingsPrefs.showWorkAppsFlow.collectAsState()
    val hiddenApps by SettingsPrefs.hiddenAppsFlow.collectAsState()

    // Build a stableKey → label lookup from the full app list (includes hidden apps)
    val appLabelMap = remember(allApps) {
        allApps.associate { it.stableKey to it.label }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Launcher Settings",
                    color = LauncherColors.Green,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("General", color = LauncherColors.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    SettingsToggle("Show Work Apps", "Show work apps on a separate swipe page.", showWorkApps) { SettingsPrefs.setShowWorkApps(it) }

                    Text("Time & Date", color = LauncherColors.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    SettingsToggle("Time", "Show current time.", hudSettings.showTime) { SettingsPrefs.updateHudSettings(hudSettings.copy(showTime = it)) }
                    SettingsToggle("Date", "Show weekday and date.", hudSettings.showDate) { SettingsPrefs.updateHudSettings(hudSettings.copy(showDate = it)) }

                    Text("Battery", color = LauncherColors.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    SettingsToggle("Battery Bar", "Show graphical battery bar.", hudSettings.showBatteryBar) { SettingsPrefs.updateHudSettings(hudSettings.copy(showBatteryBar = it)) }
                    SettingsToggle("Battery Percent", "Show numeric battery percentage.", hudSettings.showBatteryPercent) { SettingsPrefs.updateHudSettings(hudSettings.copy(showBatteryPercent = it)) }

                    Text("System HUD", color = LauncherColors.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    SettingsToggle("Voltage", "Show battery voltage.", hudSettings.showVoltage) { SettingsPrefs.updateHudSettings(hudSettings.copy(showVoltage = it)) }
                    SettingsToggle("Device Temp", "Show internal device temperature.", hudSettings.showDeviceTemp) { SettingsPrefs.updateHudSettings(hudSettings.copy(showDeviceTemp = it)) }
                    SettingsToggle("Battery Temp", "Show battery temperature.", hudSettings.showBatteryTemp) { SettingsPrefs.updateHudSettings(hudSettings.copy(showBatteryTemp = it)) }
                    SettingsToggle("Thermal Status", "Show thermal throttling status.", hudSettings.showThermal) { SettingsPrefs.updateHudSettings(hudSettings.copy(showThermal = it)) }
                    SettingsToggle("RAM (Memory)", "Show memory usage.", hudSettings.showMemory) { SettingsPrefs.updateHudSettings(hudSettings.copy(showMemory = it)) }
                    SettingsToggle("Storage", "Show storage usage.", hudSettings.showStorage) { SettingsPrefs.updateHudSettings(hudSettings.copy(showStorage = it)) }
                    SettingsToggle("Refresh Rate", "Show current display refresh rate.", hudSettings.showRefreshRate) { SettingsPrefs.updateHudSettings(hudSettings.copy(showRefreshRate = it)) }
                    SettingsToggle("Uptime", "Show system uptime.", hudSettings.showUptime) { SettingsPrefs.updateHudSettings(hudSettings.copy(showUptime = it)) }
                    SettingsToggle("Media Volume", "Show media volume percentage.", hudSettings.showMediaVolume) { SettingsPrefs.updateHudSettings(hudSettings.copy(showMediaVolume = it)) }
                    SettingsToggle("Ring Volume", "Show ringer volume percentage.", hudSettings.showRingVolume) { SettingsPrefs.updateHudSettings(hudSettings.copy(showRingVolume = it)) }

                    // ── Hidden Apps ─────────────────────────────────────────────────
                    Text(
                        "Hidden Apps",
                        color = LauncherColors.Green,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
                    )

                    if (hiddenApps.isEmpty()) {
                        Text(
                            text = "No hidden apps.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    } else {
                        hiddenApps.forEach { key ->
                            val label = appLabelMap[key] ?: key.substringBefore("|")
                            val isWorkApp = allApps.find { it.stableKey == key }?.isWorkProfile == true
                            HiddenAppRow(
                                label = label,
                                isWorkApp = isWorkApp,
                                onShow = { SettingsPrefs.unhideApp(key) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = LauncherColors.Green, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HiddenAppRow(
    label: String,
    isWorkApp: Boolean,
    onShow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp)
            if (isWorkApp) {
                Text(
                    text = "Work Profile",
                    color = LauncherColors.Green,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        TextButton(onClick = onShow) {
            Text(
                "Show",
                color = LauncherColors.Green,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
}

@Composable
fun SettingsToggle(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(desc, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = LauncherColors.Green,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}
