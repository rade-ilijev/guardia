package com.guardia.app.core.guard

import java.util.concurrent.atomic.AtomicInteger

/**
 * A claim on the front camera held by whatever the user is looking at.
 *
 * There is one process-wide CameraX provider and several things that want it: the guard's periodic
 * check, the per-app face check, the intruder capture, and the enrollment preview. The guard
 * already yielded to the per-app check; nothing told it about enrollment. So with guarding on,
 * opening "Add person" put two claimants on the same camera — and because the preview calls
 * `provider.unbindAll()` before binding, enrollment would tear down an in-flight guard capture,
 * while the next scheduled capture would steal the camera back and freeze the preview mid-pose.
 *
 * Held as a count rather than a flag: a screen that rebinds (a rotation, a recomposition that
 * re-runs the effect) briefly overlaps its own acquire and release, and a boolean would come out
 * of that unheld while the camera was still very much in use.
 *
 * This yields the camera to the *user*, never the other way round. Guarding is not stopped — the
 * service keeps running, keeps its foreground notification and resumes checks the moment the
 * preview closes — because silently disarming a security app for the duration of a screen is a
 * far worse surprise than a few minutes without a background check while the owner is plainly
 * sitting in front of the phone.
 */
object CameraLease {

    private val holders = AtomicInteger(0)

    /** True while any foreground UI is using the camera. */
    val isHeld: Boolean get() = holders.get() > 0

    fun acquire() {
        holders.incrementAndGet()
    }

    fun release() {
        // Never below zero: a stray release must not make a real hold look free.
        holders.updateAndGet { if (it > 0) it - 1 else 0 }
    }
}
