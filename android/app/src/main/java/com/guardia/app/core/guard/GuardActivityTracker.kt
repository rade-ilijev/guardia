package com.guardia.app.core.guard

import android.content.Context
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Guardia's own foreground work (face checks + camera-on time) in hourly buckets so the
 * dashboard can show how much battery the app is responsible for.
 *
 * Android does not expose true per-app battery consumption to normal apps (the OS battery screen
 * uses privileged APIs), so we derive an honest *estimate* from the camera-on time the guard loop
 * reports: camera + on-device inference is by far the dominant draw, far above the idle foreground
 * service. The estimate is clearly labelled as such in the UI.
 */
@Singleton
class GuardActivityTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Per-hour activity, keyed by epoch hour (millis / 3_600_000). */
    private val buckets = HashMap<Long, Bucket>()
    private val lock = Any()

    private val batteryCapacityMah: Double by lazy { readBatteryCapacityMah() }

    private val _activity = MutableStateFlow(GuardActivity())
    val activity: StateFlow<GuardActivity> = _activity.asStateFlow()

    init {
        scope.launch {
            runCatching { load() }
            recompute()
        }
    }

    /** Records a single completed camera check and how long the camera stayed open for it. */
    fun recordCheck(cameraOnMs: Long) {
        scope.launch {
            synchronized(lock) {
                val hour = nowHour()
                val b = buckets.getOrPut(hour) { Bucket() }
                b.checks += 1
                b.cameraMs += cameraOnMs.coerceAtLeast(0)
                prune()
            }
            recompute()
            runCatching { persist() }
        }
    }

    private fun recompute() {
        val (checks, cameraMs, hourly) = synchronized(lock) {
            prune()
            val cutoffHour = nowHour() - (HOURS - 1)
            var totalChecks = 0
            var totalCameraMs = 0L
            val series = IntArray(HOURS)
            for ((hour, b) in buckets) {
                if (hour < cutoffHour) continue
                totalChecks += b.checks
                totalCameraMs += b.cameraMs
                val idx = (hour - cutoffHour).toInt()
                if (idx in 0 until HOURS) series[idx] = b.checks
            }
            Triple(totalChecks, totalCameraMs, series.toList())
        }

        val cameraHours = cameraMs / 3_600_000.0
        val estimatedMah = cameraHours * CHECK_POWER_MW / NOMINAL_VOLTAGE
        val estimatedPercent = if (batteryCapacityMah > 0)
            (estimatedMah / batteryCapacityMah * 100.0).toFloat() else 0f

        _activity.value = GuardActivity(
            checks24h = checks,
            cameraSeconds24h = cameraMs / 1000,
            estimatedMah = estimatedMah.toInt(),
            estimatedPercent = estimatedPercent,
            hourly = hourly,
        )
    }

    private fun prune() {
        val cutoff = nowHour() - (HOURS - 1)
        val it = buckets.keys.iterator()
        while (it.hasNext()) {
            if (it.next() < cutoff) it.remove()
        }
    }

    private suspend fun persist() {
        val json = synchronized(lock) {
            val arr = JSONArray()
            for ((hour, b) in buckets) {
                arr.put(
                    JSONObject()
                        .put("h", hour)
                        .put("c", b.checks)
                        .put("m", b.cameraMs),
                )
            }
            arr.toString()
        }
        prefs.setGuardActivityLog(json)
    }

    private suspend fun load() {
        val json = prefs.guardActivityLog.first()
        if (json.isBlank()) return
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return
        synchronized(lock) {
            buckets.clear()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                buckets[o.optLong("h")] = Bucket(o.optInt("c"), o.optLong("m"))
            }
            prune()
        }
    }

    /** Reads the device's design battery capacity (mAh) via the hidden PowerProfile, with a fallback. */
    private fun readBatteryCapacityMah(): Double = runCatching {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val instance = cls.getConstructor(Context::class.java).newInstance(context)
        val mah = cls.getMethod("getBatteryCapacity").invoke(instance) as Double
        mah
    }.getOrNull()?.takeIf { it > 0 } ?: DEFAULT_CAPACITY_MAH

    private fun nowHour() = System.currentTimeMillis() / 3_600_000L

    private data class Bucket(var checks: Int = 0, var cameraMs: Long = 0)

    private companion object {
        const val HOURS = 24
        /** Approximate combined draw of the front camera + on-device face inference during a check. */
        const val CHECK_POWER_MW = 1100.0
        const val NOMINAL_VOLTAGE = 3.85
        const val DEFAULT_CAPACITY_MAH = 4000.0
    }
}

/** 24-hour rollup of Guardia's own activity and the resulting estimated battery use. */
data class GuardActivity(
    val checks24h: Int = 0,
    val cameraSeconds24h: Long = 0,
    val estimatedMah: Int = 0,
    val estimatedPercent: Float = 0f,
    /** Checks per hour for the last 24 hours, oldest first. */
    val hourly: List<Int> = List(24) { 0 },
)
