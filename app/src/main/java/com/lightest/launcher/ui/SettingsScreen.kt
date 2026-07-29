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
import com.lightest.launcher.ui.theme.LauncherColors

@Composable
fun SettingsScreen(onDismiss: () -> Unit) {
    val hudSettings by SettingsPrefs.hudSettingsFlow.collectAsState()
    val showWorkApps by SettingsPrefs.showWorkAppsFlow.collectAsState()

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
                    SettingsToggle("Show Work Apps", "Show work apps on home screen.", showWorkApps) { SettingsPrefs.setShowWorkApps(it) }

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
