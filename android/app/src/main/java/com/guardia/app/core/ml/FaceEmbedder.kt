package com.guardia.app.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

/**
 * Produces a face embedding. Uses a bundled TFLite model when available
 * (input/output shapes read dynamically so MobileFaceNet or FaceNet both work);
 * otherwise falls back to a deterministic pixel descriptor so the feature still
 * functions end-to-end. The same instance is used for enrollment and matching,
 * guaranteeing consistent dimensionality.
 *
 * **Thread safety.** A TFLite `Interpreter` is not safe for concurrent use, and this is a
 * `@Singleton` reached from the guard service's coroutine, the enrollment camera analyzer and the
 * gallery importer — which can overlap. Every entry point is `@Synchronized`, which also makes the
 * reused scratch buffers below safe.
 *
 * **Allocation.** The guard loop calls [embed] several times a minute for as long as guarding is
 * on. Each call used to allocate two direct 150 KB byte buffers, a 12 544-element int array and a
 * scaled bitmap, all of which had to be collected again moments later. They are now allocated once
 * and reused, so a recognition check produces essentially no garbage.
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

    /**
     * True when the interpreter accepted a batch-of-2 input shape, letting the upright crop and its
     * mirror be embedded in a single call. Roughly a third cheaper than two separate passes,
     * because the per-invocation overhead is paid once — but plenty of models are exported with a
     * fixed batch dimension, so this is probed at startup and quietly skipped if unsupported.
     */
    private var batchedFlip = false

    // Reused scratch. Guarded by the @Synchronized entry points.
    private var pixelBuf: IntArray = IntArray(0)
    private var inputBuf: ByteBuffer? = null
    private var batchInputBuf: ByteBuffer? = null
    private var scaled: Bitmap? = null

    // Built alongside [scaled] rather than per call: all three are invariant for a given input
    // size, and every entry point into this class is @Synchronized.
    private var scaledCanvas: android.graphics.Canvas? = null
    private var scaledRect: android.graphics.Rect? = null
    private val scalePaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private var output: Array<FloatArray> = emptyArray()
    private var batchOutput: Array<FloatArray> = emptyArray()

    /**
     * Batch dimension the interpreter's tensors are currently allocated for.
     *
     * `resizeInput` + `allocateTensors` reallocates every tensor in the graph, so doing it around
     * each inference would cost more than the batching saves. Tracking the shape means the hot path
     * ([embed], always batch 2) never resizes at all, and only a switch to the rarely-used legacy
     * path ([embedLegacy], batch 1) pays for one.
     */
    private var currentBatch = 1

    init {
        for (asset in listOf("mobilefacenet.tflite", "facenet.tflite")) {
            try {
                val model = FileUtil.loadMappedFile(context, asset)
                val options = Interpreter.Options().apply {
                    // One thread per performance core, capped: past ~4 the scheduling overhead on a
                    // model this small outweighs the parallelism, and the guard loop wants to finish
                    // fast and let the CPU idle rather than saturate it.
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                }
                val interp = Interpreter(model, options)
                val inShape = interp.getInputTensor(0).shape()   // [1, H, W, 3]
                val outShape = interp.getOutputTensor(0).shape() // [1, dim]
                inputSize = inShape[1]
                embeddingDim = outShape[outShape.size - 1]
                interpreter = interp
                usingModel = true
                allocateScratch()
                batchedFlip = probeBatchSupport(interp)
                Log.i(TAG, "Loaded $asset input=$inputSize dim=$embeddingDim batchedFlip=$batchedFlip")
                break
            } catch (t: Throwable) {
                Log.w(TAG, "Could not load $asset: ${t.message}")
            }
        }
        if (!usingModel) Log.w(TAG, "No TFLite model; using pixel-descriptor fallback")
    }

    private fun allocateScratch() {
        val floats = inputSize * inputSize * 3
        pixelBuf = IntArray(inputSize * inputSize)
        inputBuf = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder())
        output = Array(1) { FloatArray(embeddingDim) }
    }

    /** Reshapes the interpreter's input to a batch of [n], if it isn't already. */
    private fun ensureBatch(interp: Interpreter, n: Int): Boolean {
        if (currentBatch == n) return true
        return try {
            interp.resizeInput(0, intArrayOf(n, inputSize, inputSize, 3))
            interp.allocateTensors()
            currentBatch = n
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not resize input to batch $n: ${t.message}")
            false
        }
    }

    /** Tries to widen the input tensor to a batch of two; returns true if the model allows it. */
    private fun probeBatchSupport(interp: Interpreter): Boolean = try {
        interp.resizeInput(0, intArrayOf(2, inputSize, inputSize, 3))
        interp.allocateTensors()
        val ok = interp.getOutputTensor(0).shape().firstOrNull() == 2
        if (ok) {
            batchInputBuf = ByteBuffer.allocateDirect(2 * inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
            batchOutput = Array(2) { FloatArray(embeddingDim) }
        }
        // Leave the tensors in the single-image shape; ensureBatch widens them on first use.
        interp.resizeInput(0, intArrayOf(1, inputSize, inputSize, 3))
        interp.allocateTensors()
        currentBatch = 1
        ok
    } catch (t: Throwable) {
        Log.i(TAG, "Model does not support batched input (${t.message}); using two passes")
        runCatching {
            interp.resizeInput(0, intArrayOf(1, inputSize, inputSize, 3))
            interp.allocateTensors()
            currentBatch = 1
        }
        false
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
    @Synchronized
    fun embed(faceBitmap: Bitmap): FloatArray {
        val interp = interpreter ?: return fallbackEmbedding(faceBitmap)
        readPixels(faceBitmap)
        val gain = brightnessGain(pixelBuf)

        if (batchedFlip) {
            batchedEmbed(interp, gain)?.let { return it }
            // A batched run that fails once will keep failing; stop paying to find out.
            batchedFlip = false
        }
        if (!ensureBatch(interp, 1)) return fallbackEmbedding(faceBitmap)
        val base = runInference(interp, gain, flip = false) ?: return fallbackEmbedding(faceBitmap)
        val flipped = runInference(interp, gain, flip = true) ?: return base
        val combined = FloatArray(base.size) { base[it] + flipped[it] }
        return EmbeddingMath.l2Normalize(combined)
    }

    /**
     * Reproduces the original (version 0) preprocessing — a single forward pass on the plain crop with
     * fixed [-1,1] normalization and no flip/brightness adjustment. Used to compare a live face against
     * embeddings enrolled before the pipeline was upgraded, so existing enrollments keep working.
     */
    @Synchronized
    fun embedLegacy(faceBitmap: Bitmap): FloatArray {
        val interp = interpreter ?: return fallbackEmbedding(faceBitmap)
        readPixels(faceBitmap)
        val input = inputBuf ?: return fallbackEmbedding(faceBitmap)
        if (!ensureBatch(interp, 1)) return fallbackEmbedding(faceBitmap)
        input.rewind()
        for (p in pixelBuf) {
            input.putFloat((Color.red(p) - 127.5f) / 128f)
            input.putFloat((Color.green(p) - 127.5f) / 128f)
            input.putFloat((Color.blue(p) - 127.5f) / 128f)
        }
        input.rewind()
        return try {
            interp.run(input, output)
            EmbeddingMath.l2Normalize(output[0])
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy inference failed, falling back: ${t.message}")
            fallbackEmbedding(faceBitmap)
        }
    }

    /** Scales [faceBitmap] into the reused [scaled] bitmap and reads it into [pixelBuf]. */
    private fun readPixels(faceBitmap: Bitmap) {
        if (pixelBuf.size != inputSize * inputSize) allocateScratch()
        val src = if (faceBitmap.width == inputSize && faceBitmap.height == inputSize) {
            faceBitmap
        } else {
            var s = scaled
            if (s == null || s.width != inputSize || s.height != inputSize || s.isRecycled) {
                s = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
                scaled = s
                scaledCanvas = android.graphics.Canvas(s)
                scaledRect = android.graphics.Rect(0, 0, inputSize, inputSize)
            }
            val canvas = scaledCanvas
            val rect = scaledRect
            if (canvas != null && rect != null) {
                canvas.drawBitmap(faceBitmap, null, rect, scalePaint)
            }
            s
        }
        src.getPixels(pixelBuf, 0, inputSize, 0, 0, inputSize, inputSize)
    }

    /** Mild auto-exposure: scale pixels so the mean luminance approaches [TARGET_MEAN], clamped. */
    private fun brightnessGain(pixels: IntArray): Float {
        var sum = 0.0
        for (p in pixels) sum += 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)
        val mean = sum / pixels.size
        if (mean < 1.0) return 1f
        return (TARGET_MEAN / mean).toFloat().coerceIn(MIN_GAIN, MAX_GAIN)
    }

    /** Writes the (optionally mirrored) normalized crop into [buffer] at its current position. */
    private fun fill(buffer: ByteBuffer, gain: Float, flip: Boolean) {
        for (y in 0 until inputSize) {
            val rowBase = y * inputSize
            for (x in 0 until inputSize) {
                val p = pixelBuf[rowBase + if (flip) inputSize - 1 - x else x]
                buffer.putFloat(norm(Color.red(p), gain))
                buffer.putFloat(norm(Color.green(p), gain))
                buffer.putFloat(norm(Color.blue(p), gain))
            }
        }
    }

    private fun runInference(interp: Interpreter, gain: Float, flip: Boolean): FloatArray? {
        val input = inputBuf ?: return null
        input.rewind()
        fill(input, gain, flip)
        input.rewind()
        return try {
            interp.run(input, output)
            EmbeddingMath.l2Normalize(output[0])
        } catch (t: Throwable) {
            Log.w(TAG, "Inference failed: ${t.message}")
            null
        }
    }

    /** Upright + mirrored in one invocation, averaged. Returns null if the batched run fails. */
    private fun batchedEmbed(interp: Interpreter, gain: Float): FloatArray? {
        val input = batchInputBuf ?: return null
        return try {
            input.rewind()
            fill(input, gain, flip = false)
            fill(input, gain, flip = true)
            input.rewind()
            if (!ensureBatch(interp, 2)) return null
            interp.run(input, batchOutput)
            val a = EmbeddingMath.l2Normalize(batchOutput[0])
            val b = EmbeddingMath.l2Normalize(batchOutput[1])
            EmbeddingMath.l2Normalize(FloatArray(a.size) { a[it] + b[it] })
        } catch (t: Throwable) {
            Log.w(TAG, "Batched inference failed: ${t.message}")
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
        if (small !== faceBitmap) small.recycle()
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
