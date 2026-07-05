package com.guardia.app.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces a face embedding. Uses a bundled TFLite model when available
 * (input/output shapes read dynamically so MobileFaceNet or FaceNet both work);
 * otherwise falls back to a deterministic pixel descriptor so the feature still
 * functions end-to-end. The same instance is used for enrollment and matching,
 * guaranteeing consistent dimensionality.
 */
@Singleton
class FaceEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var interpreter: Interpreter? = null
    private var inputSize = 112
    private var embeddingDim = 192
    var usingModel = false
        private set

    init {
        for (asset in listOf("mobilefacenet.tflite", "facenet.tflite")) {
            try {
                val model = FileUtil.loadMappedFile(context, asset)
                val options = Interpreter.Options().apply { setNumThreads(2) }
                val interp = Interpreter(model, options)
                val inShape = interp.getInputTensor(0).shape()   // [1, H, W, 3]
                val outShape = interp.getOutputTensor(0).shape() // [1, dim]
                inputSize = inShape[1]
                embeddingDim = outShape[outShape.size - 1]
                interpreter = interp
                usingModel = true
                Log.i(TAG, "Loaded $asset input=$inputSize dim=$embeddingDim")
                break
            } catch (t: Throwable) {
                Log.w(TAG, "Could not load $asset: ${t.message}")
            }
        }
        if (!usingModel) Log.w(TAG, "No TFLite model; using pixel-descriptor fallback")
    }

    /**
     * Returns an L2-normalized embedding for the given face crop.
     *
     * Robustness measures, applied identically to enrolled faces and live probes:
     *  - **Brightness normalization** lifts dark crops toward a target mean so the same face in dim
     *    light and good light produces similar embeddings (key for watching TV in the dark, etc.).
     *  - **Flip augmentation** averages the embedding of the crop and its mirror image, which cancels
     *    left/right asymmetry and yields a more stable, discriminative vector.
     */
    fun embed(faceBitmap: Bitmap): FloatArray {
        val interp = interpreter ?: return fallbackEmbedding(faceBitmap)
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val gain = brightnessGain(pixels)

        val base = runInference(interp, pixels, gain, flip = false)
            ?: return fallbackEmbedding(faceBitmap)
        val flipped = runInference(interp, pixels, gain, flip = true)
        if (flipped == null) return base
        val combined = FloatArray(base.size) { base[it] + flipped[it] }
        return EmbeddingMath.l2Normalize(combined)
    }

    /**
     * Reproduces the original (version 0) preprocessing — a single forward pass on the plain crop with
     * fixed [-1,1] normalization and no flip/brightness adjustment. Used to compare a live face against
     * embeddings enrolled before the pipeline was upgraded, so existing enrollments keep working.
     */
    fun embedLegacy(faceBitmap: Bitmap): FloatArray {
        val interp = interpreter ?: return fallbackEmbedding(faceBitmap)
        val resized = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        val input = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            input.putFloat((Color.red(p) - 127.5f) / 128f)
            input.putFloat((Color.green(p) - 127.5f) / 128f)
            input.putFloat((Color.blue(p) - 127.5f) / 128f)
        }
        input.rewind()
        val output = Array(1) { FloatArray(embeddingDim) }
        return try {
            interp.run(input, output)
            EmbeddingMath.l2Normalize(output[0])
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy inference failed, falling back: ${t.message}")
            fallbackEmbedding(faceBitmap)
        }
    }

    /** Mild auto-exposure: scale pixels so the mean luminance approaches [TARGET_MEAN], clamped. */
    private fun brightnessGain(pixels: IntArray): Float {
        var sum = 0.0
        for (p in pixels) sum += 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
        val mean = sum / pixels.size
        if (mean < 1.0) return 1f
        return (TARGET_MEAN / mean).toFloat().coerceIn(MIN_GAIN, MAX_GAIN)
    }

    private fun runInference(interp: Interpreter, pixels: IntArray, gain: Float, flip: Boolean): FloatArray? {
        val input = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
        for (y in 0 until inputSize) {
            val rowBase = y * inputSize
            for (x in 0 until inputSize) {
                val sx = if (flip) inputSize - 1 - x else x
                val p = pixels[rowBase + sx]
                input.putFloat((norm(Color.red(p), gain)))
                input.putFloat((norm(Color.green(p), gain)))
                input.putFloat((norm(Color.blue(p), gain)))
            }
        }
        input.rewind()
        val output = Array(1) { FloatArray(embeddingDim) }
        return try {
            interp.run(input, output)
            EmbeddingMath.l2Normalize(output[0])
        } catch (t: Throwable) {
            Log.w(TAG, "Inference failed: ${t.message}")
            null
        }
    }

    private fun norm(channel: Int, gain: Float): Float {
        val v = (channel * gain).coerceAtMost(255f)
        return (v - 127.5f) / 128f
    }

    /** Downscaled grayscale descriptor; usable for verification under stable conditions. */
    private fun fallbackEmbedding(faceBitmap: Bitmap): FloatArray {
        val size = 32
        val small = Bitmap.createScaledBitmap(faceBitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        val vec = FloatArray(size * size)
        for (i in pixels.indices) {
            val p = pixels[i]
            vec[i] = (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)) / 255f
        }
        return EmbeddingMath.l2Normalize(vec)
    }

    companion object {
        private const val TAG = "FaceEmbedder"
        /** Target mean luminance (0..255) used by brightness normalization. */
        private const val TARGET_MEAN = 130.0
        private const val MIN_GAIN = 0.65f
        private const val MAX_GAIN = 2.2f
    }
}
