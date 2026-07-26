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
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Two-tier icon cache:
 *  1. Memory [LruCache] — process lifetime, keyed by stableKey
 *  2. Disk PNG under cacheDir — survives process death; invalidated via lastUpdateTime
 *
 * Disk files: `{md5(stableKey)}_{lastUpdateTime}.png`
 * Adaptive flag: sibling `{same}.png.adaptive` marker file
 */
object IconCache {
    private const val DIR_NAME = "app_icons_v2"
    private const val MAX_MEMORY_ENTRIES = 256

    private val lock = ReentrantReadWriteLock()

    private val memory = object : LruCache<String, IconEntry>(MAX_MEMORY_ENTRIES) {
        override fun sizeOf(key: String, value: IconEntry): Int = 1
    }

    private fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    private fun fileName(stableKey: String, lastUpdateTime: Long): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(stableKey.toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            for (b in digest) {
                val i = b.toInt() and 0xff
                if (i < 16) append('0')
                append(Integer.toHexString(i))
            }
        }
        return "${hex}_$lastUpdateTime.png"
    }

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
            val isAdaptive = File(file.absolutePath + ".adaptive").isFile
            IconEntry(toGpu(soft), isAdaptive)
        } catch (_: Exception) {
            null
        }
    }

    fun putDisk(
        context: Context,
        stableKey: String,
        lastUpdateTime: Long,
        entry: IconEntry
    ) {
        val file = File(dir(context), fileName(stableKey, lastUpdateTime))
        val marker = File(file.absolutePath + ".adaptive")
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
                soft.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (entry.isAdaptive) {
                if (!marker.exists()) marker.createNewFile()
            } else if (marker.exists()) {
                marker.delete()
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
        val validNames = HashSet<String>(validKeys.size * 2)
        for ((key, t) in validKeys) {
            val name = fileName(key, t)
            validNames.add(name)
            validNames.add("$name.adaptive")
        }
        d.listFiles()?.forEach { f ->
            if (f.name !in validNames) f.delete()
        }
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
