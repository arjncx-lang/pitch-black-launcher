package com.lightest.launcher.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import java.io.File

/**
 * Zero-drain device temperature reader.
 *
 * Never polls on a timer and never keeps sensors registered.
 * Call [request] only from existing passive events (battery sticky broadcast,
 * thermal status callbacks). Results are cached; re-reads are rate-limited.
 *
 * Sources (first that works on this device, then sticky):
 *  1. One-shot SensorManager sample (gyro/ambient die temp — works on MTK/Infinix)
 *  2. Raw IThermalService binder (older Android; modern builds need DEVICE_POWER)
 *  3. Sysfs thermal zones (older / unlocked devices)
 */
internal object DeviceTemperature {

    private const val THERMAL_SERVICE = "thermalservice"
    private const val THERMAL_DESCRIPTOR = "android.os.IThermalService"
    private const val TX_GET_CURRENT_TEMPERATURES = IBinder.FIRST_CALL_TRANSACTION + 3
    private const val TX_GET_CURRENT_TEMPERATURES_WITH_TYPE = IBinder.FIRST_CALL_TRANSACTION + 4

    private const val TYPE_CPU = 0
    private const val TYPE_GPU = 1
    private const val TYPE_BATTERY = 2
    private const val TYPE_SKIN = 3
    private const val TYPE_NPU = 9
    private const val TYPE_TPU = 10
    private const val TYPE_SOC = 13

    /** Minimum gap between real sensor/binder/sysfs reads (cache serves the rest). */
    private const val MIN_READ_INTERVAL_MS = 30_000L
    private const val SENSOR_TIMEOUT_MS = 400L

    private data class Sample(val type: Int, val celsius: Float)

    private enum class Source { UNRESOLVED, SENSOR, BINDER, SYSFS, UNAVAILABLE }

    @Volatile
    private var source: Source = Source.UNRESOLVED

    @Volatile
    private var cachedCelsius: Float? = null

    @Volatile
    private var lastReadElapsedMs: Long = 0L

    @Volatile
    private var thermalBinder: IBinder? = null

    @Volatile
    private var useTypedSkinCall: Boolean = false

    @Volatile
    private var cachedTempFile: File? = null

    @Volatile
    private var sensorTypeKey: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var sensorInFlight: Boolean = false

    /** Last known device °C, or null if never successfully read. */
    fun lastCelsius(): Float? = cachedCelsius

    /**
     * Request a device temperature update. Invokes [onResult] on the main thread
     * with the best available value (may be cached). Never throws.
     */
    fun request(context: Context, onResult: (Float?) -> Unit) {
        try {
            val cached = cachedCelsius
            val now = SystemClock.elapsedRealtime()
            if (cached != null && now - lastReadElapsedMs < MIN_READ_INTERVAL_MS) {
                onResult(cached)
                return
            }

            when (resolveSource(context)) {
                Source.SENSOR -> {
                    // Reserve rate-limit slot before async sample.
                    lastReadElapsedMs = SystemClock.elapsedRealtime()
                    readFromSensor(context, onResult)
                }
                Source.BINDER -> {
                    lastReadElapsedMs = SystemClock.elapsedRealtime()
                    val v = readFromBinder()
                    publish(v, onResult)
                }
                Source.SYSFS -> {
                    lastReadElapsedMs = SystemClock.elapsedRealtime()
                    val v = readFromSysfs()
                    publish(v, onResult)
                }
                else -> onResult(cached)
            }
        } catch (_: Throwable) {
            onResult(cachedCelsius)
        }
    }

    private fun publish(value: Float?, onResult: (Float?) -> Unit) {
        if (value != null) {
            cachedCelsius = value
            lastReadElapsedMs = SystemClock.elapsedRealtime()
        }
        onResult(cachedCelsius)
    }

    private fun resolveSource(context: Context): Source {
        if (source != Source.UNRESOLVED) return source

        synchronized(this) {
            if (source != Source.UNRESOLVED) return source

            val app = context.applicationContext

            // 1) On-device IMU/ambient temp sensor — no special permission.
            if (findTempSensor(app) != null) {
                source = Source.SENSOR
                return source
            }

            // 2) ThermalService binder (works pre-permission-hardening).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && initBinder()) {
                source = Source.BINDER
                return source
            }

            // 3) Sysfs
            val sysfs = resolveSysfsTempFile()
            if (sysfs != null) {
                cachedTempFile = sysfs
                source = Source.SYSFS
                return source
            }

            source = Source.UNAVAILABLE
            return source
        }
    }

    // ── Sensor one-shot ──────────────────────────────────────────────────────

    private fun findTempSensor(context: Context): Sensor? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return null

        sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)?.let {
            sensorTypeKey = "ambient"
            return it
        }
        @Suppress("DEPRECATION")
        sm.getDefaultSensor(Sensor.TYPE_TEMPERATURE)?.let {
            sensorTypeKey = "legacy"
            return it
        }

        // Vendor types e.g. android.sensor.gyro_temperature (LSM6DSO on MTK/Infinix).
        val match = sm.getSensorList(Sensor.TYPE_ALL).firstOrNull { s ->
            val st = s.stringType?.lowercase().orEmpty()
            val name = s.name.lowercase()
            (st.contains("temp") || name.contains("temp") ||
                st.contains("therm") || name.contains("therm")) &&
                !st.contains("battery") && !name.contains("battery")
        }
        if (match != null) {
            sensorTypeKey = match.stringType ?: match.name
        }
        return match
    }

    private fun readFromSensor(context: Context, onResult: (Float?) -> Unit) {
        if (sensorInFlight) {
            onResult(cachedCelsius)
            return
        }

        val app = context.applicationContext
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = if (sm != null) findTempSensor(app) else null
        if (sm == null || sensor == null) {
            // Sensor vanished — allow re-resolve.
            source = Source.UNRESOLVED
            onResult(cachedCelsius)
            return
        }

        sensorInFlight = true
        var delivered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.isEmpty()) return
                val raw = event.values[0]
                // Sensors report °C; reject garbage.
                val celsius = if (raw in 5f..99f) raw else null
                finish(celsius)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            fun finish(value: Float?) {
                if (delivered) return
                delivered = true
                try {
                    sm.unregisterListener(this)
                } catch (_: Throwable) { }
                sensorInFlight = false
                mainHandler.post { publish(value, onResult) }
            }
        }

        val registered = try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        } catch (_: Throwable) {
            false
        }

        if (!registered) {
            sensorInFlight = false
            source = Source.UNRESOLVED
            onResult(cachedCelsius)
            return
        }

        // Hard cap: never leave the sensor registered.
        mainHandler.postDelayed({
            if (!delivered) {
                try {
                    sm.unregisterListener(listener)
                } catch (_: Throwable) { }
                sensorInFlight = false
                delivered = true
                onResult(cachedCelsius)
            }
        }, SENSOR_TIMEOUT_MS)
    }

    // ── Binder (best-effort on older OS) ─────────────────────────────────────

    private fun initBinder(): Boolean {
        val binder = fetchThermalBinder() ?: return false
        if (transactTemperatures(binder, TYPE_SKIN) != null) {
            thermalBinder = binder
            useTypedSkinCall = true
            return true
        }
        if (transactTemperatures(binder, null) != null) {
            thermalBinder = binder
            useTypedSkinCall = false
            return true
        }
        return false
    }

    private fun fetchThermalBinder(): IBinder? {
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            getService.invoke(null, THERMAL_SERVICE) as? IBinder
        } catch (_: Throwable) {
            null
        }
    }

    private fun readFromBinder(): Float? {
        val binder = thermalBinder ?: return null
        if (!binder.isBinderAlive) {
            source = Source.UNRESOLVED
            thermalBinder = null
            return null
        }
        val samples = if (useTypedSkinCall) {
            transactTemperatures(binder, TYPE_SKIN)
                ?: transactTemperatures(binder, null)
        } else {
            transactTemperatures(binder, null)
        } ?: return null
        return pickDeviceTemp(samples)
    }

    private fun transactTemperatures(binder: IBinder, typeFilter: Int?): List<Sample>? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(THERMAL_DESCRIPTOR)
            val code = if (typeFilter != null) {
                data.writeInt(typeFilter)
                TX_GET_CURRENT_TEMPERATURES_WITH_TYPE
            } else {
                TX_GET_CURRENT_TEMPERATURES
            }
            if (!binder.transact(code, data, reply, 0)) return null
            reply.readException()
            val samples = readTemperatureSamples(reply)
            return samples.ifEmpty { null }
        } catch (_: Throwable) {
            return null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun readTemperatureSamples(reply: Parcel): List<Sample> {
        val n = reply.readInt()
        if (n <= 0) return emptyList()
        val out = ArrayList<Sample>(n)
        for (i in 0 until n) {
            if (reply.readInt() == 0) continue
            val value = reply.readFloat()
            val type = reply.readInt()
            reply.readString()
            reply.readInt()
            if (value in 5f..99f && type != TYPE_BATTERY) {
                out.add(Sample(type, value))
            }
        }
        return out
    }

    private fun pickDeviceTemp(samples: List<Sample>): Float? {
        if (samples.isEmpty()) return null
        val preferred = intArrayOf(
            TYPE_SKIN, TYPE_CPU, TYPE_SOC, TYPE_GPU, TYPE_NPU, TYPE_TPU
        )
        for (pref in preferred) {
            samples.firstOrNull { it.type == pref }?.let { return it.celsius }
        }
        return samples.maxByOrNull { it.celsius }?.celsius
    }

    // ── Sysfs fallback ───────────────────────────────────────────────────────

    private fun readFromSysfs(): Float? {
        val tempFile = cachedTempFile ?: return null
        return try {
            val celsius = tempFile.readText().trim().toLong() / 1000f
            if (celsius in 5f..99f) celsius else null
        } catch (_: Exception) {
            source = Source.UNRESOLVED
            cachedTempFile = null
            null
        }
    }

    private fun resolveSysfsTempFile(): File? {
        val base = File("/sys/class/thermal")
        if (!base.isDirectory) return null
        val zones = base.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") }
            ?: return null

        data class Candidate(val tempFile: File, val type: String, val celsius: Float)
        val candidates = ArrayList<Candidate>(zones.size)
        for (zone in zones) {
            val tempFile = File(zone, "temp")
            if (!tempFile.canRead()) continue
            val type = try {
                File(zone, "type").readText().trim().lowercase()
            } catch (_: Exception) {
                ""
            }
            if (type.contains("batt") || type.contains("battery") ||
                type.contains("chg") || type.contains("charger") ||
                type.contains("usb") || type.contains("pmic")
            ) continue
            val celsius = try {
                tempFile.readText().trim().toLong() / 1000f
            } catch (_: Exception) {
                continue
            }
            if (celsius !in 5f..99f) continue
            candidates.add(Candidate(tempFile, type, celsius))
        }
        if (candidates.isEmpty()) return null
        val preferred = arrayOf(
            "skin", "xo-therm", "ap", "cpu", "soc", "tsens", "big", "little", "gpu"
        )
        for (keyword in preferred) {
            candidates.firstOrNull { it.type.contains(keyword) }?.let { return it.tempFile }
        }
        return candidates.maxByOrNull { it.celsius }?.tempFile
    }
}
