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

    @Volatile private var onPreempt: (() -> Unit)? = null
    @Volatile private var onFree: (() -> Unit)? = null

    /** True while any foreground UI is using the camera. */
    val isHeld: Boolean get() = holders.get() > 0

    /**
     * Registered by the guard service for the lifetime of guarding.
     *
     * [preempt] aborts an in-flight background capture and hands the camera back immediately;
     * [free] is called when the last foreground holder lets go, so the guard can take its
     * postponed look straight away instead of waiting out a whole cadence interval.
     */
    fun setGuard(preempt: (() -> Unit)?, free: (() -> Unit)?) {
        onPreempt = preempt
        onFree = free
    }

    fun acquire() {
        holders.incrementAndGet()
    }

    /**
     * Claims the camera for something the user is waiting on — a per-app face check, the
     * enrollment preview — and takes it off the guard if the guard is mid-capture.
     *
     * The ordering matters and is the whole point: the person standing in front of the phone
     * cannot wait, and the guard's periodic check can, because it comes round again. Without this
     * the two simply raced: whichever bound last called `unbindAll()` on the other, so the check
     * the user was waiting for could open onto a torn-down camera.
     */
    fun acquireWithPriority() {
        val held = holders.incrementAndGet()
        if (held == 1) runCatching { onPreempt?.invoke() }
    }

    fun release() {
        // Never below zero: a stray release must not make a real hold look free.
        val left = holders.updateAndGet { if (it > 0) it - 1 else 0 }
        if (left == 0) runCatching { onFree?.invoke() }
    }
}
