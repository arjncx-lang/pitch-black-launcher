package com.lightest.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.lightest.launcher.R
import com.lightest.launcher.model.AppItem
import com.lightest.launcher.model.IconEntry
import com.lightest.launcher.model.PackageDetails
import com.lightest.launcher.model.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

object LauncherRepository {
    private const val PREFS_NAME = "launcher_prefs"
    private const val KEY_APP_ORDER = "app_order"
    /** Cap concurrent icon decode / disk I/O so we don't thrash CPU or storage. */
    private const val ICON_PARALLELISM = 6

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
        synchronized(this) {
            cachedOrderMap = apps.mapIndexed { index, app -> app.stableKey to index }.toMap()
        }
    }

    private fun customIconResId(pkg: String, cls: String, label: String): Int {
        return when (pkg) {
            "com.google.android.calendar" -> R.drawable.ic_custom_calendar
            "com.transsion.camera" -> R.drawable.ic_custom_camera
            "com.mixplorer.silver" -> R.drawable.ic_custom_files
            "com.android.settings" -> R.drawable.ic_custom_settings
            "com.google.android.apps.photosgo" -> R.drawable.ic_custom_gallery
            "com.google.android.calculator" -> R.drawable.ic_custom_calculator
            "com.google.android.deskclock" -> R.drawable.ic_custom_clock
            "com.transsion.smartmessage" -> R.drawable.ic_custom_messages
            "com.maxmpz.audioplayer" -> R.drawable.ic_custom_music
            "com.transsion.soundrecorder" -> R.drawable.ic_custom_recorder
            "com.sh.smart.caller" -> {
                if (cls.contains("Contact", ignoreCase = true) ||
                    label.contains("Contact", ignoreCase = true)
                ) {
                    R.drawable.ic_custom_contacts
                } else {
                    R.drawable.ic_custom_phone
                }
            }
            else -> 0
        }
    }

    /**
     * Phase 1 — metadata only.
     * No icon decode, no PackageManager.getPackageInfo, no disk I/O.
     */
    fun getInstalledApps(context: Context): List<AppItem> {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val myUserHandle = android.os.Process.myUserHandle()
        val apps = ArrayList<AppItem>(64)

        for (userHandle in userManager.userProfiles) {
            val isWorkProfile = userHandle != myUserHandle
            val activityInfos = launcherApps.getActivityList(null, userHandle)

            for (activityInfo in activityInfos) {
                val pkg = activityInfo.componentName.packageName
                val cls = activityInfo.componentName.className
                val label = activityInfo.label?.toString()?.ifBlank { pkg } ?: pkg
                val stableKey = "$pkg|$cls|${userHandle.hashCode()}"

                apps.add(
                    AppItem(
                        label = label,
                        labelLower = label.lowercase(),
                        stableKey = stableKey,
                        packageName = pkg,
                        className = cls,
                        customIconResId = customIconResId(pkg, cls, label),
                        isWorkProfile = isWorkProfile,
                        userHandle = userHandle
                    )
                )
            }
        }

        var appsList = apps.distinctBy { it.stableKey }
        val orderMap = getSavedOrderMap(context)
        appsList = if (orderMap.isNotEmpty()) {
            appsList.sortedWith(
                compareBy<AppItem> { orderMap[it.stableKey] ?: Int.MAX_VALUE }
                    .thenBy { it.labelLower }
            )
        } else {
            appsList.sortedWith(
                compareBy<AppItem> {
                    when (it.packageName) {
                        "com.sh.smart.caller", "com.android.dialer" ->
                            if (!it.label.contains("Contact", ignoreCase = true)) 0 else 5
                        "com.transsion.smartmessage", "com.google.android.apps.messaging" -> 1
                        "com.google.android.apps.photosgo", "com.sec.android.gallery3d" -> 2
                        "com.android.chrome" -> 3
                        else -> 4
                    }
                }.thenBy { it.labelLower }
            )
        }
        return appsList
    }

    /**
     * Phase 2 — memory → disk → decode waterfall, bounded parallelism.
     * [onIcon] is always invoked on [Dispatchers.Main.immediate].
     */
    suspend fun loadIcons(
        context: Context,
        apps: List<AppItem>,
        onIcon: (stableKey: String, entry: IconEntry) -> Unit
    ) {
        val appContext = context.applicationContext
        val density = appContext.resources.displayMetrics.density
        val iconSizePx = (48 * density).toInt().coerceIn(72, 192)
        val densityDpi = appContext.resources.displayMetrics.densityDpi
        val pm = appContext.packageManager
        val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val semaphore = Semaphore(ICON_PARALLELISM)
        val updateTimes = ConcurrentHashMap<String, Long>(apps.size)

        // Instant memory hits (after process was warm or prior load this session).
        for (app in apps) {
            coroutineContext.ensureActive()
            val mem = IconCache.getMemory(app.stableKey)
            if (mem != null) {
                withContext(Dispatchers.Main.immediate) {
                    onIcon(app.stableKey, mem)
                }
            }
        }

        coroutineScope {
            apps.map { app ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()

                        val lastUpdate = packageLastUpdate(pm, app.packageName)
                        updateTimes[app.stableKey] = lastUpdate

                        // Already in memory (delivered above, or raced from another load).
                        IconCache.getMemory(app.stableKey)?.let { return@withPermit }

                        val disk = IconCache.getDisk(appContext, app.stableKey, lastUpdate)
                        if (disk != null) {
                            IconCache.putMemory(app.stableKey, disk)
                            withContext(Dispatchers.Main.immediate) {
                                onIcon(app.stableKey, disk)
                            }
                            return@withPermit
                        }

                        val entry = decodeIcon(
                            app, appContext, pm, launcherApps, densityDpi, iconSizePx
                        ) ?: return@withPermit

                        IconCache.putMemory(app.stableKey, entry)
                        IconCache.putDisk(appContext, app.stableKey, lastUpdate, entry)
                        withContext(Dispatchers.Main.immediate) {
                            onIcon(app.stableKey, entry)
                        }
                    }
                }
            }.awaitAll()
        }

        withContext(Dispatchers.IO) {
            if (updateTimes.isNotEmpty()) {
                IconCache.pruneOrphans(appContext, updateTimes)
            }
        }
    }

    private fun packageLastUpdate(pm: PackageManager, packageName: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0).lastUpdateTime
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun decodeIcon(
        app: AppItem,
        context: Context,
        pm: PackageManager,
        launcherApps: LauncherApps,
        densityDpi: Int,
        iconSizePx: Int
    ): IconEntry? {
        return try {
            var isAdaptive = false
            val drawable = if (app.customIconResId != 0) {
                ContextCompat.getDrawable(context, app.customIconResId)!!
            } else {
                val activityInfo = app.userHandle?.let { uh ->
                    launcherApps.getActivityList(app.packageName, uh)
                        .firstOrNull { it.componentName.className == app.className }
                }
                val base = try {
                    activityInfo?.getIcon(densityDpi)
                        ?: pm.getActivityIcon(ComponentName(app.packageName, app.className))
                } catch (_: Exception) {
                    pm.defaultActivityIcon
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    base is AdaptiveIconDrawable
                ) {
                    val bg = base.background
                    val fg = base.foreground
                    if (bg != null && fg != null) {
                        isAdaptive = true
                        LayerDrawable(arrayOf(bg, fg))
                    } else {
                        fg ?: base
                    }
                } else {
                    base
                }
            }

            val soft = drawable.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
            IconEntry(IconCache.toGpu(soft), isAdaptive)
        } catch (_: Exception) {
            null
        }
    }

    /** Dialog-only: version + APK size. Never called for every app on the home grid. */
    fun getPackageDetails(context: Context, packageName: String): PackageDetails {
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val rawVersion = pkgInfo.versionName ?: "1.0"
            val cleanedVersion = if (rawVersion.length > 14) {
                "v" + rawVersion.take(12) + "…"
            } else {
                if (rawVersion.startsWith("v", ignoreCase = true)) rawVersion else "v$rawVersion"
            }
            val sourceDir = pkgInfo.applicationInfo?.sourceDir
            val size = if (!sourceDir.isNullOrEmpty()) {
                val f = File(sourceDir)
                if (f.exists()) formatFileSize(f.length()) else "Unknown"
            } else {
                "Unknown"
            }
            PackageDetails(versionName = cleanedVersion, formattedSize = size)
        } catch (_: Exception) {
            PackageDetails()
        }
    }

    /**
     * Ranked filter: prefix matches first, then substring.
     * Single pass with two ArrayLists — no intermediate Sequence allocations.
     */
    fun filterApps(apps: List<AppItem>, rawQuery: String): List<AppItem> {
        if (rawQuery.isEmpty()) return apps
        val q = if (rawQuery == rawQuery.lowercase()) rawQuery else rawQuery.lowercase()
        if (q.isEmpty()) return apps

        val prefix = ArrayList<AppItem>()
        val substring = ArrayList<AppItem>()
        for (app in apps) {
            val label = app.labelLower
            val pkg = app.packageName
            when {
                label.startsWith(q) || pkg.startsWith(q) -> prefix.add(app)
                label.contains(q) || pkg.contains(q) -> substring.add(app)
            }
        }
        if (substring.isEmpty()) return prefix
        if (prefix.isEmpty()) return substring
        return prefix.apply { addAll(substring) }
    }
}
