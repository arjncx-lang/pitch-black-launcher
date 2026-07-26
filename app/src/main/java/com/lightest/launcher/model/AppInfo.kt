package com.lightest.launcher.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Lightweight app row model — no bitmaps, no package-info I/O.
 *
 * @Immutable lets Compose skip equality deep-dives for AppCard.
 *
 * Pre-computed fields avoid allocs on hot paths:
 *  - labelLower : search (no per-keystroke lowercase())
 *  - stableKey  : LazyVerticalGrid keys + order prefs + icon cache keys
 */
@Immutable
data class AppItem(
    val label: String,
    val labelLower: String,
    val stableKey: String,
    val packageName: String,
    val className: String,
    val isWorkProfile: Boolean = false,
    val userHandle: android.os.UserHandle? = null
)

/** GPU-ready icon plus adaptive crop flag (known only after decode). */
@Immutable
data class IconEntry(
    val bitmap: ImageBitmap,
    val isAdaptive: Boolean
)

/** Loaded only when the detail dialog opens — never on home-grid cold start. */
@Immutable
data class PackageDetails(
    val versionName: String = "v1.0",
    val formattedSize: String = "Unknown"
)

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups])
}
