package com.guardia.app.core.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.guardia.app.R
import com.guardia.app.core.ml.AnalysisConfig
import com.guardia.app.core.ml.BitmapUtils
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Grabs a short burst of front-camera frames (best-effort) and stores them encrypted. Started
 * from [GuardDeviceAdminReceiver] on a wrong device unlock and from the PIN gates.
 *
 * A single frame often catches the intruder mid-motion or before auto-exposure settles; a burst
 * of [BURST_COUNT] shots spaced [BURST_SPACING_MS] apart turns "maybe evidence" into evidence,
 * while still keeping the camera open for well under ~4 seconds total.
 *
 * Note: capturing while the keyguard is up from a background-started service is
 * restricted on newer Android and varies by OEM; this is best-effort by design.
 */
@AndroidEntryPoint
class IntruderCaptureService : LifecycleService() {

    @Inject lateinit var intruders: IntruderRepository
    @Inject lateinit var events: EventsRepository

    private val executor = Executors.newSingleThreadExecutor()
    private val shotCount = java.util.concurrent.atomic.AtomicInteger(0)
    /** Frames seen since the camera opened; the first few are discarded for auto-exposure. */
    private val frameCount = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var lastShotAt = 0L
    private val finished = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var cameraTracked = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: "Wrong unlock"
        // Best-effort: if the OS refuses the foreground start (restricted context on newer Android),
        // log the attempt and bail rather than crashing the process.
        if (!startAsForeground()) {
            lifecycleScope.launch {
                runCatching { events.log(GuardEvent.Type.WRONG_UNLOCK, "$source - capture unavailable") }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (hasCameraPermission()) {
            startCapture(source)
            lifecycleScope.launch {
                delay(BURST_TIMEOUT_MS)
                finish()
            }
        } else {
            lifecycleScope.launch {
                events.log(GuardEvent.Type.WRONG_UNLOCK, "$source - camera unavailable")
                finish()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(source: String) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                provider = cameraProvider
                val analysis = AnalysisConfig.builder().build()
                analysis.setAnalyzer(executor) { proxy ->
                    val rotation = proxy.imageInfo.rotationDegrees
                    // Discard warm-up frames (auto-exposure settling) and pace the burst.
                    val now = android.os.SystemClock.elapsedRealtime()
                    val due = frameCount.incrementAndGet() > WARMUP_FRAMES &&
                        shotCount.get() < BURST_COUNT &&
                        (lastShotAt == 0L || now - lastShotAt >= BURST_SPACING_MS)
                    if (!due) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    lastShotAt = now
                    val raw = runCatching { proxy.toBitmap() }.getOrNull()
                    proxy.close()
                    if (raw == null) return@setAnalyzer
                    val shot = shotCount.incrementAndGet()
                    // Apply the sensor rotation so the stored selfie is upright, not sideways.
                    val bmp = runCatching { BitmapUtils.rotate(raw, rotation) }.getOrNull() ?: raw
                    lifecycleScope.launch {
                        val path = runCatching {
                            intruders.saveCapture(BitmapUtils.toJpeg(bmp), source)
                        }.getOrNull()
                        // One timeline entry per incident (the extra shots land in the
                        // Intruders gallery alongside it).
                        if (shot == 1) {
                            events.log(GuardEvent.Type.WRONG_UNLOCK, "$source - photos captured", path)
                        }
                        if (shot >= BURST_COUNT) finish()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                if (!cameraTracked) { GuardiaCameraMic.enterCamera(); cameraTracked = true }
            }.onFailure {
                lifecycleScope.launch {
                    events.log(GuardEvent.Type.WRONG_UNLOCK, "$source - capture failed")
                    finish()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun finish() {
        // Called from both the burst completion and the timeout watchdog; run teardown once.
        if (!finished.compareAndSet(false, true)) return
        runCatching { provider?.unbindAll() }
        if (cameraTracked) { GuardiaCameraMic.exitCamera(); cameraTracked = false }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Returns false instead of throwing if the OS refuses the foreground start. */
    private fun startAsForeground(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Intruder capture", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Security check")
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val typed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasCameraPermission()) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        if (typed.isSuccess) return true
        // The camera-typed start was refused — almost always because we're a background process on
        // Android 12+ without an exemption (grant "Display over other apps" to fix). Log the real
        // reason so a device test shows it, then try a plain start as a last resort.
        android.util.Log.w(
            "IntruderCapture",
            "camera FGS start refused (background camera restriction — needs overlay permission)",
            typed.exceptionOrNull(),
        )
        return runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
    }

    companion object {
        private const val CHANNEL_ID = "guardia_intruder_capture"
        private const val NOTIFICATION_ID = 1002
        const val EXTRA_SOURCE = "source"
        /** Shots per incident. */
        private const val BURST_COUNT = 3
        /** Spacing between shots — long enough that the intruder has moved/looked up. */
        private const val BURST_SPACING_MS = 700L
        /** Frames discarded after the camera opens so auto-exposure can settle. */
        private const val WARMUP_FRAMES = 2
        /** Hard cap on how long the camera may stay open for one incident. */
        private const val BURST_TIMEOUT_MS = 5_000L

        fun start(context: Context, source: String) {
            val intent = Intent(context, IntruderCaptureService::class.java).putExtra(EXTRA_SOURCE, source)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
