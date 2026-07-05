package com.guardia.app.core.system

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar

/**
 * Forces the screen to maximum brightness so the front camera can see a face in the dark, without
 * launching an Activity (it uses the app's "display over other apps" permission, so it works from
 * the guard foreground service).
 *
 * Two modes:
 *  - BRIGHTEN (white = false): a fully transparent window that only pins screen brightness to 100%.
 *    The user keeps seeing their current screen, just brighter. This is the first, least-intrusive
 *    step.
 *  - FLOOD (white = true): an opaque white "loading" screen (with a neutral spinner) that emits the
 *    most light possible. This is the fallback used only if brightening alone wasn't enough.
 *
 * Sets the overlay window's own screenBrightness, so the change is temporary and reverts the instant
 * the overlay is removed.
 */
object BrightnessOverlay {

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var container: FrameLayout? = null
    private var spinner: View? = null
    private var hideRunnable: Runnable? = null
    @Volatile private var flooding = false

    fun canDraw(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    val isShowing: Boolean get() = container != null

    /** True once we've escalated to the opaque white flood screen. */
    val isFlooding: Boolean get() = flooding

    /**
     * Shows (or updates) the overlay. [white] = false pins brightness while staying transparent;
     * [white] = true shows the opaque white flood screen. Auto-removes after [autoHideMs] unless
     * refreshed by another call (a safety net so the screen can never get stuck bright).
     */
    fun show(context: Context, white: Boolean, autoHideMs: Long) {
        if (!canDraw(context)) return
        main.post {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            if (container == null) {
                val c = FrameLayout(context)
                val pb = ProgressBar(context)
                c.addView(
                    pb,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER },
                )
                spinner = pb
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    // Purely visual: never steal focus or touches (so the user is never trapped),
                    // and pin brightness to max while visible.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply { screenBrightness = 1f }
                runCatching {
                    wm.addView(c, params)
                    container = c
                }
            }
            applyMode(white)
            hideRunnable?.let { main.removeCallbacks(it) }
            val r = Runnable { hide(context) }
            hideRunnable = r
            main.postDelayed(r, autoHideMs)
        }
    }

    private fun applyMode(white: Boolean) {
        flooding = white
        val c = container ?: return
        if (white) {
            c.setBackgroundColor(Color.WHITE)
            spinner?.visibility = View.VISIBLE
        } else {
            // Transparent: just a brightness boost over whatever the user is looking at.
            c.setBackgroundColor(Color.TRANSPARENT)
            spinner?.visibility = View.GONE
        }
    }

    fun hide(context: Context) {
        main.post {
            hideRunnable?.let { main.removeCallbacks(it) }
            hideRunnable = null
            val c = container ?: return@post
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(c) }
            container = null
            spinner = null
            flooding = false
        }
    }
}
