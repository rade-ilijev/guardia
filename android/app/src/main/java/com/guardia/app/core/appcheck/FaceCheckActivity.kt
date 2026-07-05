package com.guardia.app.core.appcheck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.ui.theme.GuardiaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class FaceCheckActivity : ComponentActivity() {

    @Inject lateinit var appTriggerManager: com.guardia.app.core.guard.AppTriggerManager
    @Inject lateinit var overlayController: OverlayController

    private val viewModel: FaceCheckViewModel by viewModels()
    private var pkg: String = ""

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraTracked = false

    /** A frame of the underlying app, captured for the blur/freeze styles (null otherwise). */
    private var appShot: ImageBitmap? = null
    private var appShotBlurred: ImageBitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Appear instantly with no enter animation so the underlying app doesn't flash.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val appLabel = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("this app")

        // Without camera permission we can't verify — reveal the app rather than trapping the user.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            appTriggerManager.markPassed(pkg)
            finish()
            return
        }

        // Grab the app frame captured just before launch (blur/freeze styles); prep a cheap
        // downscaled copy so the blur reads well even on devices without RenderEffect.
        runCatching {
            val bmp = ScreenCaptureStore.take()
            if (bmp != null) {
                appShot = bmp.asImageBitmap()
                appShotBlurred = downscale(bmp, 0.08f).asImageBitmap()
            }
        }

        onBackPressedDispatcher.addCallback(this) { goHome() }
        viewModel.start(pkg, appLabel)
        startCamera()

        setContent {
            GuardiaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                androidx.compose.runtime.LaunchedEffect(state.flash) {
                    setScreenBrightness(state.flash)
                }

                androidx.compose.runtime.LaunchedEffect(state.phase) {
                    when (state.phase) {
                        CheckPhase.PASSED -> {
                            releaseCamera()
                            appTriggerManager.markPassed(pkg)
                            delay(450)
                            finish()
                        }
                        CheckPhase.FAILED -> {
                            releaseCamera()
                            delay(950)
                            // Close the guarded app first (bring the launcher forward), then lock the
                            // device so an unauthorized person can't keep using the phone or reopen it.
                            if (state.closeApp) goHome() else { appTriggerManager.markPassed(pkg); finish() }
                            if (state.lockDevice) {
                                delay(150)
                                DeviceAdminManager.lockNow(this@FaceCheckActivity)
                            }
                        }
                        CheckPhase.CHECKING -> Unit
                    }
                }

                FaceCheckContent(
                    appLabel = appLabel,
                    phase = state.phase,
                    message = state.message,
                    flash = state.flash,
                    style = state.style,
                    shot = appShot,
                    blurredShot = appShotBlurred,
                )
            }
        }
    }

    private fun downscale(src: Bitmap, factor: Float): Bitmap {
        val w = (src.width * factor).toInt().coerceAtLeast(1)
        val h = (src.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** Headless front-camera analysis: no preview surface, so the camera is never shown on screen. */
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            cameraProvider = provider
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                val rotation = proxy.imageInfo.rotationDegrees
                val bmp = runCatching { proxy.toBitmap() }.getOrNull()
                proxy.close()
                if (bmp != null) viewModel.onFrame(bmp, rotation)
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                if (!cameraTracked) { com.guardia.app.core.system.GuardiaCameraMic.enterCamera(); cameraTracked = true }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun releaseCamera() {
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        if (cameraTracked) { com.guardia.app.core.system.GuardiaCameraMic.exitCamera(); cameraTracked = false }
    }

    private fun setScreenBrightness(max: Boolean) {
        val lp = window.attributes
        lp.screenBrightness = if (max) 1f else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    override fun onResume() {
        super.onResume()
        // Our (opaque, same-colored) window is now drawn; remove the instant cover seamlessly.
        overlayController.hideCover()
    }

    override fun onDestroy() {
        overlayController.hideCover()
        appTriggerManager.checkInProgress = false
        releaseCamera()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun goHome() {
        // Prefer the accessibility global HOME action — it reliably backgrounds the guarded app on
        // OEM launchers where a HOME intent can be ignored. Fall back to the intent otherwise.
        val viaAccessibility = com.guardia.app.core.system.GuardAccessibilityService.goHome()
        if (!viaAccessibility) {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { startActivity(home) }
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}

@Composable
private fun FaceCheckContent(
    appLabel: String,
    phase: CheckPhase,
    message: String,
    flash: Boolean,
    style: CheckStyle,
    shot: ImageBitmap?,
    blurredShot: ImageBitmap?,
) {
    // Blur/freeze: show the actual app content, obscured, behind a small status badge.
    if (shot != null && (style == CheckStyle.BLUR || style == CheckStyle.FREEZE)) {
        BackdropCheckContent(appLabel, phase, message, style, shot, blurredShot)
    } else {
        LoadingCheckContent(appLabel, phase, message, flash)
    }
}

/** Opaque "verifying" screen with a big spinner — the default style (and the no-screenshot fallback). */
@Composable
private fun LoadingCheckContent(appLabel: String, phase: CheckPhase, message: String, flash: Boolean) {
    // While checking in the dark, paint the screen white to act as a front-facing flash.
    val flashing = flash && phase == CheckPhase.CHECKING
    val background = if (flashing) Color.White else MaterialTheme.colorScheme.background
    val accent = when {
        flashing -> Color(0xFF111817)
        phase == CheckPhase.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val onBackground = if (flashing) Color(0xFF111817) else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (flashing) Color(0xFF111817) else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .border(3.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = phase, label = "facecheck") { p ->
                Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                    when (p) {
                        CheckPhase.CHECKING -> CircularProgressIndicator(color = accent, modifier = Modifier.size(56.dp))
                        CheckPhase.PASSED -> ResultIcon(Icons.Filled.CheckCircle, accent)
                        CheckPhase.FAILED -> ResultIcon(Icons.Filled.Close, accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            when (phase) {
                CheckPhase.PASSED -> "Verified"
                CheckPhase.FAILED -> "Access blocked"
                CheckPhase.CHECKING -> "Verifying it's you"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = titleColor,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (phase == CheckPhase.CHECKING) "Hold still while Guardia checks before opening $appLabel" else message,
            style = MaterialTheme.typography.bodyMedium,
            color = onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Stealth check screen: shows ONLY the captured app frame — blurred or frozen — with no spinner,
 * badge, lock icon, or text. To an onlooker it looks like the app briefly glitched or hung, never
 * like an authentication prompt. On a pass the real app is revealed; on a failure the app is simply
 * closed (handled by the host). This hides the fact that the user has protection on this app.
 */
@Composable
private fun BackdropCheckContent(
    appLabel: String,
    phase: CheckPhase,
    message: String,
    style: CheckStyle,
    shot: ImageBitmap,
    blurredShot: ImageBitmap?,
) {
    val blur = style == CheckStyle.BLUR
    // For blur, draw the downscaled copy (reads as blur on every device) and add RenderEffect on 12+.
    val image = if (blur && blurredShot != null) blurredShot else shot
    Box(Modifier.fillMaxSize()) {
        val imageModifier = Modifier
            .fillMaxSize()
            .let { if (blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(26.dp) else it }
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.FillBounds,
        )
        // Blur: a light scrim keeps content unreadable while still looking like the app (a glitch),
        // not a lock screen. Freeze: a barely-there scrim so it reads as a hung/static frame.
        // No text, icons, or spinner — nothing that reveals a security check is happening.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (blur) 0.18f else 0.06f)))
    }
}

@Composable
private fun ResultIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.5f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f))
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(84.dp).scale(scale.value))
}
