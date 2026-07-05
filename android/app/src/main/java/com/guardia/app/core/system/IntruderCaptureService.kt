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
import com.guardia.app.core.ml.BitmapUtils
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.IntruderRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Grabs a single front-camera frame (best-effort) and stores it encrypted. Started
 * from [GuardDeviceAdminReceiver] on a wrong device unlock.
 *
 * Note: capturing while the keyguard is up from a background-started service is
 * restricted on newer Android and varies by OEM; this is best-effort by design.
 */
@AndroidEntryPoint
class IntruderCaptureService : LifecycleService() {

    @Inject lateinit var intruders: IntruderRepository
    @Inject lateinit var events: EventsRepository

    private val executor = Executors.newSingleThreadExecutor()
    private val captured = AtomicBoolean(false)
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
                delay(4000)
                if (!captured.get()) finish()
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
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    if (captured.compareAndSet(false, true)) {
                        val rotation = proxy.imageInfo.rotationDegrees
                        val raw = runCatching { proxy.toBitmap() }.getOrNull()
                        proxy.close()
                        // Apply the sensor rotation so the stored selfie is upright, not sideways.
                        val bmp = raw?.let { runCatching { BitmapUtils.rotate(it, rotation) }.getOrNull() ?: it }
                        if (bmp != null) {
                            lifecycleScope.launch {
                                val path = runCatching {
                                    intruders.saveCapture(BitmapUtils.toJpeg(bmp), source)
                                }.getOrNull()
                                events.log(GuardEvent.Type.WRONG_UNLOCK, "$source - photo captured", path)
                                finish()
                            }
                        } else {
                            finish()
                        }
                    } else {
                        proxy.close()
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
        runCatching { provider?.unbindAll() }
        if (cameraTracked) { GuardiaCameraMic.exitCamera(); cameraTracked = false }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        return runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
    }

    companion object {
        private const val CHANNEL_ID = "guardia_intruder_capture"
        private const val NOTIFICATION_ID = 1002
        const val EXTRA_SOURCE = "source"

        fun start(context: Context, source: String) {
            val intent = Intent(context, IntruderCaptureService::class.java).putExtra(EXTRA_SOURCE, source)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
