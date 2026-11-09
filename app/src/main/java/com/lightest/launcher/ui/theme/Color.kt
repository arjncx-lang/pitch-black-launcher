package com.lightest.launcher.ui.theme

import androidx.compose.ui.graphics.Color

/** Centralized color tokens. */
object LauncherColors {
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF9500)
    val CardBackground = Color(0xFF1A1A1A)
    val TrackBackground = Color(0xFF333333)

    // Palette interface to support White / Dark Theme cleanly
    data class ThemeColors(
        val isWhite: Boolean,
        val background: Color,
        val surface: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val border: Color,
        val cardBackground: Color,
        val divider: Color,
        val iconTint: Color
    )

    val DarkPalette = ThemeColors(
        isWhite = false,
        background = Color.Black,
        surface = Color.Black,
        textPrimary = Color.White,
        textSecondary = Color.White.copy(alpha = 0.7f),
        border = Color(0xFF263E26),
        cardBackground = Color(0xFF0C0E0C),
        divider = Color(0xFF1E281E),
        iconTint = Color.White
    )

    val WhitePalette = ThemeColors(
        isWhite = true,
        background = Color(0xFFF7F7F7),
        surface = Color.White,
        textPrimary = Color(0xFF111111),
        textSecondary = Color(0xFF555555),
        border = Color(0xFFDDDDDD),
        cardBackground = Color.White,
        divider = Color(0xFFEEEEEE),
        iconTint = Color(0xFF222222)
    )
}