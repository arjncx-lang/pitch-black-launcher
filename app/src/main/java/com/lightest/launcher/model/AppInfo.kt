package com.lightest.launcher.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * @Immutable: Compose skips all equality checks for this type → zero spurious AppCard recompositions.
 *
 * Pre-computed fields:
 *  - labelLower : used by search; avoids a new String allocation per app per keystroke
 *  - stableKey  : used by LazyVerticalGrid key lambda; avoids String concat on every composition
 */
@Immutable
data class AppItem(
    val label: String,
    val labelLower: String,         // pre-computed once for zero-alloc search
    val stableKey: String,          // pre-computed once for grid key lambda
    val packageName: String,
    val className: String,
    val versionName: String,
    val formattedSize: String,
    val iconBitmap: ImageBitmap,    // Hardware bitmap: lives in GPU memory, zero CPU-GPU transfer per frame
    val isAdaptiveIcon: Boolean = false,
    val isWorkProfile: Boolean = false,
    val userHandle: android.os.UserHandle? = null
)

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups.coerceAtMost(units.size - 1)])
}
