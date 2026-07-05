package com.guardia.app.core.appcheck

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.guardia.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows an instant, opaque full-screen cover via the window manager the moment a guarded app opens,
 * so the app is never visible during the brief latency before [FaceCheckActivity] is drawn. The
 * cover paints the same color as the activity window, so removing it is seamless. Requires the
 * "Display over other apps" permission; degrades gracefully (no cover) when not granted.
 */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private val autoHide = Runnable { hideCover() }

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun showCover() {
        if (!canShow()) return
        main.post {
            if (cover != null) return@post
            val view = View(context).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.guardia_overlay_bg))
            }
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            )
            runCatching {
                windowManager.addView(view, params)
                cover = view
                // Safety: never leave a cover stuck if the activity fails to appear.
                main.postDelayed(autoHide, SAFETY_TIMEOUT_MS)
            }
        }
    }

    fun hideCover() {
        main.removeCallbacks(autoHide)
        main.post {
            cover?.let { runCatching { windowManager.removeViewImmediate(it) } }
            cover = null
        }
    }

    private companion object {
        const val SAFETY_TIMEOUT_MS = 6000L
    }
}
