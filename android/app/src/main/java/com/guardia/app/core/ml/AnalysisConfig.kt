package com.guardia.app.core.ml

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy

/**
 * Shared capture configuration for every `ImageAnalysis` use case in the app.
 *
 * All four capture paths (guard loop, per-app face check, intruder capture, enrollment preview)
 * previously built an `ImageAnalysis` with no resolution constraint at all, which leaves the choice
 * to CameraX's device-dependent default. That is a problem in both directions: on a device that
 * picks 640x480, a face at arm's length is only about 100 pixels wide — barely more than the
 * embedder's 112px input, so the crop is upscaled and detail the model needs simply isn't there. On
 * a device that picks 1080p, every check pays to decode a 6 MB frame to find the same face.
 *
 * Pinning [TARGET] makes the trade-off explicit and identical on every device: roughly 200 pixels
 * across a typical face, which is comfortably above what the embedder consumes, at a quarter of
 * 1080p's decode cost. It also means an embedding computed on one phone is built from the same
 * amount of real detail as one computed on another, which matters because the acceptance threshold
 * is a single global number.
 */
object AnalysisConfig {

    /** Preferred analysis resolution; CameraX falls back to the closest supported size. */
    private val TARGET = Size(1280, 720)

    private val selector: ResolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(TARGET, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
        )
        .build()

    /**
     * An `ImageAnalysis` configured for face work: latest-frame-only (an old frame is worse than no
     * frame when deciding who is holding the phone right now) at the pinned resolution.
     */
    fun builder(): ImageAnalysis.Builder = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(selector)
}
