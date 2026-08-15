package com.lightest.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lightest.launcher.model.IconEntry
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Two-tier icon cache:
 *  1. Memory [LruCache] — process lifetime, sized by bitmap bytes (not count)
 *  2. Disk WebP under cacheDir — survives process death; invalidated via lastUpdateTime
 *
 * Disk files: `{hash(stableKey)}_{lastUpdateTime}.webp`
 * v4 bitmaps have adaptive padding baked in (no per-frame 1.5x scale).
 */
object IconCache {
    private const val DIR_NAME = "app_icons_v4"
    /** Use 1/8 of available heap for icon memory cache. */
    private val MAX_MEMORY_BYTES: Int = (Runtime.getRuntime().maxMemory() / 8).toInt()

    private val lock = ReentrantReadWriteLock()

    private val memory = object : LruCache<String, IconEntry>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: IconEntry): Int {
            val bmp = value.bitmap.asAndroidBitmap()
            return bmp.allocationByteCount
        }
    }

    @Volatile private var droppedLegacyCache = false

    private fun dir(context: Context): File {
        if (!droppedLegacyCache) {
            droppedLegacyCache = true
            File(context.cacheDir, "app_icons_v3").deleteRecursively()
        }
        return File(context.cacheDir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }
    }

    /** Fast, non-cryptographic hash — no need for MD5 overhead. */
    private fun keyHash(stableKey: String): String {
        var h = stableKey.hashCode().toLong() and 0xFFFFFFFFL
        h = h xor (h ushr 16)
        h *= 0x45d9f3bL
        h = h xor (h ushr 16)
        return h.toString(16)
    }

    private fun fileName(stableKey: String, lastUpdateTime: Long): String =
        "${keyHash(stableKey)}_$lastUpdateTime.webp"

    fun getMemory(stableKey: String): IconEntry? = lock.read { memory.get(stableKey) }

    fun putMemory(stableKey: String, entry: IconEntry) {
        lock.write { memory.put(stableKey, entry) }
    }

    fun removeMemoryByPackage(packageName: String) {
        lock.write {
            val prefix = "$packageName|"
            memory.snapshot().keys.forEach { key ->
                if (key.startsWith(prefix)) memory.remove(key)
            }
        }
    }

    fun clearMemory() {
        lock.write { memory.evictAll() }
    }

    /**
     * Decode from disk if present. Returns a hardware ImageBitmap when possible.
     * Must be called off the main thread.
     */
    fun getDisk(context: Context, stableKey: String, lastUpdateTime: Long): IconEntry? {
        val file = File(dir(context), fileName(stableKey, lastUpdateTime))
        if (!file.isFile) return null
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val soft = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            IconEntry(toGpu(soft), isAdaptive = false)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun putDisk(
        context: Context,
        stableKey: String,
        lastUpdateTime: Long,
        entry: IconEntry
    ) {
        val file = File(dir(context), fileName(stableKey, lastUpdateTime))
        try {
            val androidBmp = entry.bitmap.asAndroidBitmap()
            val soft = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                androidBmp.config == Bitmap.Config.HARDWARE
            ) {
                androidBmp.copy(Bitmap.Config.ARGB_8888, false) ?: return
            } else {
                androidBmp
            }
            FileOutputStream(file).use { out ->
                // WebP is ~40% smaller and faster to encode than PNG
                val format = if (Build.VERSION.SDK_INT >= 30)
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                else
                    Bitmap.CompressFormat.WEBP
                soft.compress(format, 100, out)
            }
            if (soft !== androidBmp) soft.recycle()
        } catch (_: Exception) {
            // Disk full — memory cache still works.
        }
    }

    /**
     * Remove disk files whose names are not in the current valid set.
     * [validKeys]: stableKey → lastUpdateTime from the latest metadata scan.
     */
    fun pruneOrphans(context: Context, validKeys: Map<String, Long>) {
        val d = dir(context)
        val validNames = HashSet<String>(validKeys.size)
        for ((key, t) in validKeys) {
            validNames.add(fileName(key, t))
        }
        d.listFiles()?.forEach { f ->
            if (f.name !in validNames) f.delete()
        }
    }

    /** Drop disk files for apps that are no longer installed (hash prefix only). */
    fun pruneUninstalled(context: Context, stableKeys: Collection<String>) {
        val validHashes = HashSet<String>(stableKeys.size)
        for (key in stableKeys) validHashes.add(keyHash(key))
        dir(context).listFiles()?.forEach { f ->
            val hash = f.name.substringBefore('_')
            if (hash !in validHashes) f.delete()
        }
    }

    /**
     * Adaptive icons use a 108dp viewport with a 72dp safe zone (2/3).
     * Crop the extra padding once so Compose does not scale every frame.
     */
    fun cropAdaptiveSafeZone(src: Bitmap): Bitmap {
        val insetX = src.width / 6
        val insetY = src.height / 6
        val w = (src.width - 2 * insetX).coerceAtLeast(1)
        val h = (src.height - 2 * insetY).coerceAtLeast(1)
        if (insetX == 0 && insetY == 0) return src
        val cropped = Bitmap.createBitmap(src, insetX, insetY, w, h)
        if (cropped !== src) src.recycle()
        return cropped
    }

    fun toGpu(soft: Bitmap): ImageBitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                val hard = soft.copy(Bitmap.Config.HARDWARE, false)
                soft.recycle()
                hard.asImageBitmap()
            } catch (_: Exception) {
                soft.asImageBitmap()
            }
        }
        return soft.asImageBitmap()
    }
}
