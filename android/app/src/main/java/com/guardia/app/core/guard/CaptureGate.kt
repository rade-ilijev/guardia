package com.guardia.app.core.guard

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Battery gate for the capture loop. Captures only while the device is awake AND unlocked.
 *
 * Free tier: a fixed cadence from the responsiveness profile (battery saver / balanced / max).
 * Premium: an optional check the instant the device unlocks, an editable "ramp" of faster early
 * checks that then relaxes to the steady interval, a custom steady interval, and shake-to-check.
 */
@Singleton
class CaptureGate @Inject constructor(
    @ApplicationContext private val context: Context,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastMagnitude = 0f
    private var lastShakeAt = 0L
    private var lastCaptureAt = 0L

    /** Steady cadence between checks once any unlock ramp has been exhausted. */
    @Volatile private var tierCadenceMs = BALANCED_MS

    /** When false, periodic checks are disabled and only triggers cause a capture. */
    @Volatile private var intervalEnabled = true

    /** Premium override in ms; 0 = use the responsiveness profile cadence. */
    @Volatile private var customIntervalMs = 0L

    // --- Premium unlock scheduling ---
    @Volatile private var firstCheckOnUnlock = false
    /** Cumulative offsets (ms) from unlock at which the ramp checks fire (after the first). */
    @Volatile private var rampOffsetsMs: LongArray = LongArray(0)
    @Volatile private var shakeEnabled = false

    /** Start time (ms) of the current unlocked session, or 0 when locked/off. */
    @Volatile private var sessionStartAt = 0L
    /** Index into [rampOffsetsMs] of the next ramp check. */
    @Volatile private var rampIndex = 0
    /** Set when a one-off capture is due (first-check-on-unlock or a shake). */
    @Volatile private var immediatePending = false

    /** Adjust the steady cadence from the responsiveness profile (0 = saver, 1 = balanced, 2 = max). */
    fun setResponsiveness(level: Int) {
        tierCadenceMs = when (level) {
            0 -> SAVER_MS
            2 -> MAX_MS
            else -> BALANCED_MS
        }
    }

    fun setIntervalEnabled(enabled: Boolean) { intervalEnabled = enabled }

    /** seconds <= 0 clears the custom interval (falls back to the profile cadence). */
    fun setCustomIntervalSeconds(seconds: Int) {
        customIntervalMs = if (seconds > 0) seconds * 1000L else 0L
    }

    fun setFirstCheckOnUnlock(value: Boolean) { firstCheckOnUnlock = value }

    fun setShakeEnabled(value: Boolean) { shakeEnabled = value }

    /** [gapsSeconds] are the delays between consecutive early checks after unlock. */
    fun setRamp(gapsSeconds: List<Int>) {
        var cumulative = 0L
        rampOffsetsMs = gapsSeconds
            .filter { it > 0 }
            .map { gap -> cumulative += gap * 1000L; cumulative }
            .toLongArray()
    }

    /** Request a one-off capture as soon as the screen is on (used by app-open triggers). */
    fun requestImmediate() { immediatePending = true }

    /** Called when the device becomes unlocked (USER_PRESENT): begins a new check session. */
    fun onUnlocked() {
        val now = System.currentTimeMillis()
        sessionStartAt = now
        rampIndex = 0
        if (firstCheckOnUnlock) {
            immediatePending = true
            lastCaptureAt = 0L
        } else {
            // No first check: wait a full cadence before the first steady check.
            lastCaptureAt = now
        }
    }

    /** Called when the screen turns off / device locks: ends the session so the ramp re-arms. */
    fun onScreenOff() {
        sessionStartAt = 0L
        rampIndex = 0
        immediatePending = false
    }

    fun start() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun shouldCapture(): Boolean {
        // Only check while the phone is awake AND already unlocked (in active use).
        // While the screen is off or sitting on the lock screen, guarding stays idle — the
        // wrong-unlock intruder selfie (Device Admin) covers failed lock-screen attempts instead.
        if (!powerManager.isInteractive) return false
        if (keyguardManager.isKeyguardLocked) {
            if (sessionStartAt != 0L) onScreenOff()
            return false
        }
        // First time we observe an unlocked device without an active session (e.g. service just
        // started while already unlocked): open a session so scheduling behaves consistently.
        if (sessionStartAt == 0L) onUnlocked()

        val now = System.currentTimeMillis()
        if (immediatePending) {
            immediatePending = false
            lastCaptureAt = now
            return true
        }
        if (!intervalEnabled) return false

        // Ramp phase: fire at each cumulative offset from unlock, then fall through to steady.
        if (rampIndex < rampOffsetsMs.size) {
            val due = sessionStartAt + rampOffsetsMs[rampIndex]
            if (now >= due) {
                rampIndex++
                lastCaptureAt = now
                return true
            }
            return false
        }

        val cadence = if (customIntervalMs > 0L) customIntervalMs else tierCadenceMs
        if (now - lastCaptureAt < cadence) return false
        lastCaptureAt = now
        return true
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = event.values
        val magnitude = sqrt(x * x + y * y + z * z)
        val delta = kotlin.math.abs(magnitude - lastMagnitude)
        lastMagnitude = magnitude
        if (shakeEnabled && delta > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                lastShakeAt = now
                immediatePending = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        // Steady cadence between background face checks. Tuned so guarding actually catches an
        // intruder in the moment (the old 1-5 min cadence felt like "nothing happens"): Max security
        // checks every few seconds, Balanced roughly twice a minute, Saver stays battery-light.
        const val SAVER_MS = 90 * 1000L          // 1.5 minutes
        const val BALANCED_MS = 25 * 1000L       // 25 seconds
        const val MAX_MS = 6 * 1000L             // 6 seconds
        const val SHAKE_THRESHOLD = 6f           // m/s^2 jolt to count as a deliberate shake
        const val SHAKE_COOLDOWN_MS = 4000L      // don't re-trigger on the same shake
    }
}
