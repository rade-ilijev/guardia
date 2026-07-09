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
 * Optional on-device sex/gender estimate from a face crop, used only by the (experimental,
 * opt-in) appearance rules. This is a *hook*: it runs a bundled TFLite model if one is present at
 * `assets/gender.tflite`, and otherwise reports [AppearanceAnalyzer.Sex.UNKNOWN] so the feature is
 * simply inert. Nothing ever leaves the device.
 *
 * Model I/O contract (so any compatible classifier drops in):
 *  - Input: one tensor shaped [1, H, W, 3], float RGB normalized to [0,1]. H/W are read from the model.
 *  - Output: either
 *      • size-2 `[P(female), P(male)]` (argmax), or
 *      • size-1 `P(male)` (>= 0.5 → male).
 *  - A prediction below [MIN_CONFIDENCE] is reported as UNKNOWN.
 *
 * Gender/sex is sensitive and error-prone; it is off unless the user enables appearance rules AND a
 * model is installed, and it can still only *relax* locking — never cause a lock.
 */
@Singleton
class GenderClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var interpreter: Interpreter? = null
    private var inputSize = 96

    /** True when a gender model is bundled and loaded, so the feature can actually run. */
    var isAvailable = false
        private set

    init {
        try {
            val model = FileUtil.loadMappedFile(context, ASSET)
            val interp = Interpreter(model, Interpreter.Options().apply { setNumThreads(1) })
            inputSize = interp.getInputTensor(0).shape()[1]
            interpreter = interp
            isAvailable = true
            Log.i(TAG, "Loaded $ASSET input=$inputSize")
        } catch (t: Throwable) {
            Log.i(TAG, "No $ASSET bundled; gender estimate disabled")
        }
    }

    fun classify(faceCrop: Bitmap): AppearanceAnalyzer.Sex {
        val interp = interpreter ?: return AppearanceAnalyzer.Sex.UNKNOWN
        return try {
            val resized = Bitmap.createScaledBitmap(faceCrop, inputSize, inputSize, true)
            val input = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
            val pixels = IntArray(inputSize * inputSize)
            resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            for (p in pixels) {
                input.putFloat(Color.red(p) / 255f)
                input.putFloat(Color.green(p) / 255f)
                input.putFloat(Color.blue(p) / 255f)
            }
            input.rewind()
            val outLen = interp.getOutputTensor(0).shape().let { it[it.size - 1] }
            val output = Array(1) { FloatArray(outLen) }
            interp.run(input, output)
            val o = output[0]
            when {
                outLen >= 2 -> {
                    val pMale = o[1]; val pFemale = o[0]
                    val conf = kotlin.math.max(pMale, pFemale)
                    when {
                        conf < MIN_CONFIDENCE -> AppearanceAnalyzer.Sex.UNKNOWN
                        pMale >= pFemale -> AppearanceAnalyzer.Sex.MALE
                        else -> AppearanceAnalyzer.Sex.FEMALE
                    }
                }
                else -> {
                    val pMale = o[0]
                    when {
                        kotlin.math.abs(pMale - 0.5f) < (MIN_CONFIDENCE - 0.5f) -> AppearanceAnalyzer.Sex.UNKNOWN
                        pMale >= 0.5f -> AppearanceAnalyzer.Sex.MALE
                        else -> AppearanceAnalyzer.Sex.FEMALE
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gender inference failed: ${t.message}")
            AppearanceAnalyzer.Sex.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "GenderClassifier"
        private const val ASSET = "gender.tflite"
        /** Minimum winning-class probability to report a concrete sex (else UNKNOWN). */
        private const val MIN_CONFIDENCE = 0.7f
    }
}
