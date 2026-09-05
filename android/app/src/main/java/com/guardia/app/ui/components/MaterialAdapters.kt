package com.guardia.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Radius
import com.guardia.app.ui.theme.Spacing

/*
 * ---------------------------------------------------------------------------------------------
 * Drop-in shadcn replacements for the Material 3 components the screens already call.
 *
 * Material 3's own widgets can't be re-shaped from the theme — a Material Button is a 40dp *pill*
 * no matter what `MaterialTheme.shapes` says, its OutlinedTextField floats a label through a notch
 * in its own border, and its Switch is 52x32 with a growing thumb. None of that is shadcn.
 *
 * Rather than edit several thousand call sites, each of these keeps the Material 3 name and
 * parameter names and renders the shadcn component instead. A screen converts by changing its
 * import from `androidx.compose.material3.Button` to `com.guardia.app.ui.components.Button`.
 * ---------------------------------------------------------------------------------------------
 */

/** Material `Button` -> shadcn `<Button>` (default variant). */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = ShButton(onClick, modifier, ButtonVariant.Default, ButtonSize.Default, enabled, content = content)

/** Material `OutlinedButton` -> shadcn `<Button variant="outline">`. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = ShButton(onClick, modifier, ButtonVariant.Outline, ButtonSize.Default, enabled, content = content)

/** Material `TextButton` -> shadcn `<Button variant="ghost">`. */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = ShButton(onClick, modifier, ButtonVariant.Ghost, ButtonSize.Default, enabled, content = content)

/** Material `FilledTonalButton` -> shadcn `<Button variant="secondary">`. */
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = ShButton(onClick, modifier, ButtonVariant.Secondary, ButtonSize.Default, enabled, content = content)

/** Material `Switch` -> shadcn `<Switch>`. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ShSwitch(checked, onCheckedChange, modifier, enabled)

/** Material `RadioButton` -> shadcn `<RadioGroupItem>`. */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ShRadio(selected, onClick, modifier, enabled)

/** Material `Checkbox` -> shadcn `<Checkbox>`. */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ShCheckbox(checked, onCheckedChange, modifier, enabled)

/** Material `LinearProgressIndicator` -> shadcn `<Progress>`. */
@Composable
fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Guardia.colors.primary,
    trackColor: Color = Guardia.colors.muted,
) = ShProgress(progress(), modifier, 8.dp, color, trackColor)

/**
 * Material `CircularProgressIndicator` -> a thin spinning arc.
 *
 * shadcn has no spinner component; its convention is a rotating `Loader2` lucide icon at
 * `text-muted-foreground`, which is what this draws.
 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Guardia.colors.mutedForeground,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing),
        ),
        label = "spinnerAngle",
    )
    Canvas(modifier.size(24.dp)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color.copy(alpha = 0.2f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawArc(
            color = color,
            startAngle = angle, sweepAngle = 90f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

/** Material `HorizontalDivider` -> shadcn `<Separator>`. */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = Guardia.colors.border,
) = Box(modifier.fillMaxWidth().height(thickness).background(color))

/**
 * Material `FilterChip` -> shadcn `<ToggleGroupItem>`: a bordered pill that fills with
 * `bg-secondary` when selected, rather than Material's tonal container plus leading checkmark.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val c = Guardia.colors
    val shape = RoundedCornerShape(Radius.md)
    val bg by animateColorAsState(
        if (selected) c.secondary else Color.Transparent,
        tween(140),
        label = "chipBg",
    )
    val borderColor by animateColorAsState(
        if (selected) c.foreground.copy(alpha = 0.25f) else c.border,
        tween(140),
        label = "chipBorder",
    )
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (selected) c.foreground else c.mutedForeground
        CompositionLocalProvider(
            LocalContentColor provides fg,
            LocalTextStyle provides
                MaterialTheme.typography.labelMedium.copy(color = fg),
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(6.dp))
            }
            label()
            if (trailingIcon != null) {
                Spacer(Modifier.width(6.dp))
                trailingIcon()
            }
        }
    }
}

/**
 * Material `OutlinedTextField` -> shadcn's form field: `<Label>` above the control, `<Input>`
 * below it, and helper or error text in `text-xs` under that.
 *
 * Material floats the label through a notch cut in the border; shadcn never does, because a label
 * that is always in the same place is easier to scan down a form.
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(Radius.md),
) {
    val c = Guardia.colors
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            CompositionLocalProvider(
                LocalContentColor provides c.foreground,
                LocalTextStyle provides
                    MaterialTheme.typography.titleSmall.copy(color = c.foreground),
            ) { label() }
            Spacer(Modifier.height(Spacing.sm))
        }
        ShInput(
            value = value,
            onValueChange = { if (!readOnly) onValueChange(it) },
            placeholder = null,
            enabled = enabled,
            isError = isError,
            singleLine = singleLine,
            minHeight = if (minLines > 1) (40 + (minLines - 1) * 20).dp else 40.dp,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            leadingIcon = null,
            trailingContent = trailingIcon,
            placeholderContent = placeholder,
            leadingContent = leadingIcon,
        )
        if (supportingText != null) {
            Spacer(Modifier.height(6.dp))
            CompositionLocalProvider(
                LocalContentColor provides if (isError) c.destructiveForeground else c.mutedForeground,
                LocalTextStyle provides
                    MaterialTheme.typography.bodySmall.copy(
                        color = if (isError) c.destructiveForeground else c.mutedForeground,
                    ),
            ) { supportingText() }
        }
    }
}

/**
 * Material `Badge` -> shadcn `<Badge variant="destructive">`; used for unread counts, so it stays
 * a circle-ish pill.
 */
@Composable
fun Badge(
    modifier: Modifier = Modifier,
    containerColor: Color = Guardia.colors.destructive,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(18.dp)
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalTextStyle provides
                MaterialTheme.typography.labelSmall.copy(color = Color.White),
        ) { content?.invoke(this) }
    }
}
