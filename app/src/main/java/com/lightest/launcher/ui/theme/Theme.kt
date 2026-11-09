package com.lightest.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.lightest.launcher.data.SettingsPrefs

private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFF34C759),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White
)

private val LightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color(0xFF34C759),
    onSecondary = Color.White,
    background = Color(0xFFF7F7F7),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    val whiteTheme by SettingsPrefs.whiteThemeFlow.collectAsState()
    MaterialTheme(
        colorScheme = if (whiteTheme) LightScheme else DarkScheme,
        typography = Typography,
        content = content
    )
}
