package com.guardia.app.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guardia.app.core.ml.AnalysisConfig
import java.util.concurrent.Executors

/**
 * Front-camera preview that streams frames to [onFrame] (bitmap + rotation degrees)
 * for live analysis. Used by guided enrollment.
 */
@Composable
fun AnalysisCamera(
    onFrame: (Bitmap, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    androidx.compose.runtime.DisposableEffect(Unit) {
        // Claim the camera for the foreground before binding. The guard's periodic check polls
        // this and skips while it is held, which is what stops the two from tearing each other's
        // binding down — this composable calls provider.unbindAll(), so without the lease starting
        // an enrollment would kill an in-flight guard capture and the next scheduled one would
        // steal the preview back mid-pose.
        com.guardia.app.core.guard.CameraLease.acquireWithPriority()
        com.guardia.app.core.system.GuardiaCameraMic.enterCamera()
        onDispose {
            com.guardia.app.core.system.GuardiaCameraMic.exitCamera()
            com.guardia.app.core.guard.CameraLease.release()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = AnalysisConfig.builder().build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val rotation = proxy.imageInfo.rotationDegrees
                        val bmp = runCatching { proxy.toBitmap() }.getOrNull()
                        proxy.close()
                        if (bmp != null) onFrame(bmp, rotation)
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                }.onFailure { Log.e("AnalysisCamera", "bind failed", it) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
