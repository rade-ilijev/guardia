package com.guardia.app.ui.components

import android.util.LruCache

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.guardia.app.ui.theme.Guardia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decoded launcher icons, kept between compositions.
 *
 * Without this every scroll that takes a row off screen and back decodes its icon again: the
 * `produceState` is keyed on the package, but the whole composable is disposed and recreated, so
 * the key buys nothing across a scroll. Thirty rows of that is thirty PackageManager lookups and
 * thirty bitmap decodes for pictures the app already had.
 *
 * Small on purpose — 64 icons at 96x96 ARGB is about 2.4MB, which is worth it for a list that is
 * scrolled constantly, and bounded so a device with a thousand apps cannot grow it without limit.
 */
private val iconCache = LruCache<String, ImageBitmap>(64)

/**
 * Another app's launcher icon.
 *
 * Loaded off the main thread and keyed on the package, because `getApplicationIcon` hits the
 * package manager and decodes a drawable — cheap once, and a stutter when a list of thirty of them
 * does it during layout. While it loads, and for a package that has since been uninstalled, the
 * slot holds a muted tile so a list never reflows as icons arrive.
 *
 * Always decorative: every caller sits it beside the app's name, and a screen reader announcing
 * "WhatsApp" twice is worse than not announcing the picture at all.
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(iconCache[packageName], packageName) {
        // A cache hit is the initial value, so a scrolled-back row draws its icon on the first
        // frame instead of flashing the placeholder and settling a moment later.
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }?.also { iconCache.put(packageName, it) }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.clip(shape).clearAndSetSemantics { },
        )
    } else {
        Box(modifier.clip(shape).background(Guardia.colors.muted).clearAndSetSemantics { })
    }
}
