package com.guardia.app.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Best-effort, fully on-device estimate of coarse visible appearance (hair colour, eye tone) from a
 * detected face. This is a heuristic — it samples pixels around ML Kit landmarks and above the face —
 * so it is deliberately coarse and reports [Appearance.confidence]; callers must treat a low
 * confidence (or UNKNOWN) as "don't know" and never act on a guess. Nothing here leaves the device.
 *
 * Intentionally NOT included: sex/gender and ethnicity. Those cannot be estimated reliably or fairly
 * from pixels without a dedicated, evaluated classifier model, and misclassifying them to drive a
 * security decision is both unreliable and sensitive. Hair/eye tone are descriptive and reversible.
 */
@Singleton
class AppearanceAnalyzer @Inject constructor() {

    enum class HairColor { DARK, BROWN, BLONDE, RED, GRAY, UNKNOWN }
    enum class EyeTone { DARK, LIGHT, UNKNOWN }

    data class Appearance(
        val hair: HairColor,
        val eyes: EyeTone,
        val confidence: Float,
    ) {
        /** Human-readable summary for evidence labels, e.g. "Dark hair · light eyes". Null if unknown. */
        fun summary(): String? {
            val parts = buildList {
                when (hair) {
                    HairColor.DARK -> add("dark hair")
                    HairColor.BROWN -> add("brown hair")
                    HairColor.BLONDE -> add("blonde hair")
                    HairColor.RED -> add("red hair")
                    HairColor.GRAY -> add("gray hair")
                    HairColor.UNKNOWN -> {}
                }
                when (eyes) {
                    EyeTone.DARK -> add("dark eyes")
                    EyeTone.LIGHT -> add("light eyes")
                    EyeTone.UNKNOWN -> {}
                }
            }
            return if (parts.isEmpty()) null else parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
        }
    }

    /** [upright] must already have the sensor rotation applied (same frame ML Kit detected on). */
    fun analyze(upright: Bitmap, face: Face): Appearance {
        val hair = estimateHair(upright, face)
        val eyes = estimateEyes(upright, face)
        // Confidence is the fraction of the two signals we actually resolved.
        val resolved = (if (hair.second) 1 else 0) + (if (eyes.second) 1 else 0)
        return Appearance(hair.first, eyes.first, resolved / 2f)
    }

    /** Samples a band just above the forehead. Returns the class and whether we're reasonably sure. */
    private fun estimateHair(bmp: Bitmap, face: Face): Pair<HairColor, Boolean> {
        val box = face.boundingBox
        val bandH = (box.height() * 0.45f).toInt().coerceAtLeast(6)
        val top = (box.top - bandH).coerceAtLeast(0)
        val bottom = box.top.coerceIn(1, bmp.height)
        if (bottom - top < 4) return HairColor.UNKNOWN to false
        // Central 55% of the face width, where hair (not background) is most likely.
        val inset = (box.width() * 0.225f).toInt()
        val left = (box.left + inset).coerceIn(0, bmp.width - 2)
        val right = (box.right - inset).coerceIn(left + 1, bmp.width)

        var rs = 0.0; var gs = 0.0; var bs = 0.0; var n = 0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val c = bmp.getPixel(x, y)
                rs += Color.red(c); gs += Color.green(c); bs += Color.blue(c); n++
                x += 3
            }
            y += 3
        }
        if (n < 12) return HairColor.UNKNOWN to false
        val r = rs / n; val g = gs / n; val b = bs / n
        val hsv = FloatArray(3)
        Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
        val hue = hsv[0]; val sat = hsv[1]; val value = hsv[2]

        return when {
            value < 0.22f -> HairColor.DARK to true
            sat < 0.16f && value > 0.5f -> HairColor.GRAY to true
            (hue < 20f || hue >= 345f) && sat > 0.35f -> HairColor.RED to true
            hue in 20f..45f && value > 0.62f && sat in 0.2f..0.7f -> HairColor.BLONDE to true
            hue in 15f..45f -> HairColor.BROWN to true
            value < 0.4f -> HairColor.DARK to true
            else -> HairColor.UNKNOWN to false
        }
    }

    /** Samples the iris area at each eye landmark; classifies dark vs light. */
    private fun estimateEyes(bmp: Bitmap, face: Face): Pair<EyeTone, Boolean> {
        val eyes = listOfNotNull(
            face.getLandmark(FaceLandmark.LEFT_EYE)?.position,
            face.getLandmark(FaceLandmark.RIGHT_EYE)?.position,
        )
        if (eyes.isEmpty()) return EyeTone.UNKNOWN to false
        val radius = (face.boundingBox.width() * 0.03f).toInt().coerceIn(2, 8)
        var darkVotes = 0; var lightVotes = 0
        for (p in eyes) {
            var rs = 0.0; var gs = 0.0; var bs = 0.0; var n = 0
            var y = (p.y.toInt() - radius).coerceAtLeast(0)
            val yEnd = min(p.y.toInt() + radius, bmp.height - 1)
            while (y <= yEnd) {
                var x = (p.x.toInt() - radius).coerceAtLeast(0)
                val xEnd = min(p.x.toInt() + radius, bmp.width - 1)
                while (x <= xEnd) {
                    val c = bmp.getPixel(x, y)
                    rs += Color.red(c); gs += Color.green(c); bs += Color.blue(c); n++
                    x++
                }
                y++
            }
            if (n < 4) continue
            val hsv = FloatArray(3)
            Color.RGBToHSV((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt(), hsv)
            // Iris pixels are typically the darker part sampled; a low value = dark (brown/black),
            // a higher value with some blue/green saturation = light (blue/green/gray).
            val value = hsv[2]; val hue = hsv[0]; val sat = hsv[1]
            val light = value > 0.5f && (sat > 0.18f && hue in 150f..270f || value > 0.7f)
            if (light) lightVotes++ else darkVotes++
        }
        if (darkVotes == 0 && lightVotes == 0) return EyeTone.UNKNOWN to false
        return if (darkVotes >= lightVotes) EyeTone.DARK to true else EyeTone.LIGHT to true
    }

    companion object {
        /** Rules should only act on an estimate at or above this confidence. */
        const val MIN_ACTIONABLE_CONFIDENCE = 0.5f
    }
}
