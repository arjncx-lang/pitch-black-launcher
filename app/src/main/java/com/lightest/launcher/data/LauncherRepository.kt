package com.lightest.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.lightest.launcher.R
import com.lightest.launcher.model.AppItem
import com.lightest.launcher.model.formatFileSize
import java.io.File

object LauncherRepository {
    private const val PREFS_NAME = "launcher_prefs"
    private const val KEY_APP_ORDER = "app_order"

    // Cache the saved order in memory so we don't hit SharedPreferences disk on every call.
    // Invalidated when saveAppOrder is called.
    @Volatile private var cachedOrderMap: Map<String, Int>? = null

    private fun getSavedOrderMap(context: Context): Map<String, Int> {
        cachedOrderMap?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val orderString = prefs.getString(KEY_APP_ORDER, null)
        val map = orderString
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapIndexed { index, id -> id to index }
            ?.toMap()
            ?: emptyMap()
        cachedOrderMap = map
        return map
    }

    fun saveAppOrder(context: Context, apps: List<AppItem>) {
        val orderString = apps.joinToString(",") { it.stableKey }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_APP_ORDER, orderString).apply()
        // Rebuild cache immediately so next read is instant
        synchronized(this) {
            cachedOrderMap = apps.mapIndexed { index, app -> app.stableKey to index }.toMap()
        }
    }

    /**
     * Rasterizes a Drawable into an ImageBitmap at exactly [sizePx].
     *
     * On API 26+, copies to Config.HARDWARE so pixels live in GPU VRAM permanently.
     * Falls back to software bitmap silently if GPU memory is full (no crash).
     */
    private fun rasterizeToGpu(
        drawable: android.graphics.drawable.Drawable,
        sizePx: Int
    ): androidx.compose.ui.graphics.ImageBitmap {
        val soft = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                val hard = soft.copy(Bitmap.Config.HARDWARE, false)
                soft.recycle()   // Free software copy from CPU RAM immediately
                hard.asImageBitmap()
            } catch (_: Exception) {
                // GPU VRAM full or hardware copy unsupported — keep software bitmap.
                // Silent fallback: icons still display, just with per-frame CPU-GPU upload.
                soft.asImageBitmap()
            }
        }
        return soft.asImageBitmap()
    }

    fun getInstalledApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        // Compute density-aware icon size once — decode at exactly the display size,
        // not an arbitrary 128px that wastes memory or causes GPU downscaling.
        val density = context.resources.displayMetrics.density
        val iconSizePx = (48 * density).toInt().coerceIn(72, 192)

        val apps = mutableListOf<AppItem>()
        val myUserHandle = android.os.Process.myUserHandle()

        for (userHandle in userManager.userProfiles) {
            val isWorkProfile = userHandle != myUserHandle
            val activityInfos = launcherApps.getActivityList(null, userHandle)

            for (activityInfo in activityInfos) {
                val pkg = activityInfo.componentName.packageName
                val cls = activityInfo.componentName.className
                val label = activityInfo.label?.toString()?.ifBlank { pkg } ?: pkg

                val customIconResId = when (pkg) {
                    "com.google.android.calendar"      -> R.drawable.ic_custom_calendar
                    "com.transsion.camera"             -> R.drawable.ic_custom_camera
                    "com.mixplorer.silver"             -> R.drawable.ic_custom_files
                    "com.android.settings"             -> R.drawable.ic_custom_settings
                    "com.google.android.apps.photosgo" -> R.drawable.ic_custom_gallery
                    "com.google.android.calculator"    -> R.drawable.ic_custom_calculator
                    "com.google.android.deskclock"     -> R.drawable.ic_custom_clock
                    "com.transsion.smartmessage"       -> R.drawable.ic_custom_messages
                    "com.maxmpz.audioplayer"           -> R.drawable.ic_custom_music
                    "com.transsion.soundrecorder"      -> R.drawable.ic_custom_recorder
                    "com.sh.smart.caller" -> {
                        if (cls.contains("Contact", ignoreCase = true) ||
                            label.contains("Contact", ignoreCase = true)) {
                            R.drawable.ic_custom_contacts
                        } else {
                            R.drawable.ic_custom_phone
                        }
                    }
                    else -> null
                }

                var isAdaptive = false
                val drawable = if (customIconResId != null) {
                    ContextCompat.getDrawable(context, customIconResId)!!
                } else {
                    val base = try {
                        pm.getActivityIcon(activityInfo.componentName)
                    } catch (_: PackageManager.NameNotFoundException) {
                        activityInfo.getIcon(0)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        base is android.graphics.drawable.AdaptiveIconDrawable) {
                        val bg = base.background
                        val fg = base.foreground
                        if (bg != null && fg != null) {
                            isAdaptive = true
                            android.graphics.drawable.LayerDrawable(arrayOf(bg, fg))
                        } else fg ?: base
                    } else base
                }

                // Rasterize → GPU bitmap; the Drawable is released after this line.
                val iconBitmap = rasterizeToGpu(drawable, iconSizePx)

                var rawVersion = "1.0"
                var formattedSize = "Unknown"
                try {
                    val pkgInfo = pm.getPackageInfo(pkg, 0)
                    rawVersion = pkgInfo.versionName ?: "1.0"
                    val sourceDir = pkgInfo.applicationInfo?.sourceDir
                    if (!sourceDir.isNullOrEmpty()) {
                        val f = File(sourceDir)
                        if (f.exists()) formattedSize = formatFileSize(f.length())
                    }
                } catch (_: Exception) { /* non-critical */ }

                val cleanedVersion = if (rawVersion.length > 14) {
                    "v" + rawVersion.take(12) + "…"
                } else {
                    if (rawVersion.startsWith("v", ignoreCase = true)) rawVersion else "v$rawVersion"
                }

                // stableKey is pre-computed here once and reused everywhere (grid key, order map, prefs)
                val stableKey = "$pkg|$cls|${userHandle.hashCode()}"

                apps.add(
                    AppItem(
                        label        = label,
                        labelLower   = label.lowercase(),   // pre-computed for zero-alloc search
                        stableKey    = stableKey,
                        packageName  = pkg,
                        className    = cls,
                        versionName  = cleanedVersion,
                        formattedSize= formattedSize,
                        iconBitmap   = iconBitmap,
                        isAdaptiveIcon = isAdaptive,
                        isWorkProfile  = isWorkProfile,
                        userHandle     = userHandle
                    )
                )
            }
        }

        var appsList = apps.distinctBy { it.stableKey }

        val orderMap = getSavedOrderMap(context)
        if (orderMap.isNotEmpty()) {
            appsList = appsList.sortedWith(
                compareBy<AppItem> { orderMap[it.stableKey] ?: Int.MAX_VALUE }
                    .thenBy { it.labelLower }
            )
        } else {
            // Default first-launch sort: Phone → Messages → Gallery → Browser → rest
            appsList = appsList.sortedWith(
                compareBy<AppItem> {
                    when (it.packageName) {
                        "com.sh.smart.caller", "com.android.dialer" ->
                            if (!it.label.contains("Contact", ignoreCase = true)) 0 else 5
                        "com.transsion.smartmessage", "com.google.android.apps.messaging" -> 1
                        "com.google.android.apps.photosgo", "com.sec.android.gallery3d"  -> 2
                        "com.android.chrome" -> 3
                        else -> 4
                    }
                }.thenBy { it.labelLower }
            )
        }

        return appsList
    }
}
