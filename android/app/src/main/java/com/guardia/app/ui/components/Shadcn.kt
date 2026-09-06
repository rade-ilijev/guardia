package com.guardia.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.MonoCaption
import com.guardia.app.ui.theme.Radius
import com.guardia.app.ui.theme.Spacing
import com.guardia.app.ui.theme.StatusReadout

/*
 * ---------------------------------------------------------------------------------------------
 * shadcn/ui, ported to Jetpack Compose.
 *
 * Each composable below is a direct translation of the corresponding shadcn component: same
 * variants, same sizes, same token names, same visual result. The class strings from the original
 * are quoted in the doc comments so the two can be diffed by eye.
 *
 * Two things do not survive the port and are replaced with the platform-correct equivalent:
 *   - `hover:` has no meaning on a touchscreen, so every hover state becomes a *pressed* state.
 *   - `focus-visible:ring-*` becomes a focus ring on text fields only, where Android actually has
 *     a focus concept.
 * ---------------------------------------------------------------------------------------------
 */

// =============================================================================================
// Button
// =============================================================================================

enum class ButtonVariant { Default, Secondary, Outline, Ghost, Destructive, Link }

enum class ButtonSize { Sm, Default, Lg, Icon }

@Immutable
private data class ButtonStyle(
    val container: Color,
    /** When set, the fill is this ramp instead of [container] — how the brand reaches the CTA. */
    val brush: Brush?,
    val content: Color,
    val border: Color?,
    val pressedOverlay: Color,
    /** Halo colour behind the button. Null for the quiet variants, which must stay quiet. */
    val glow: Color?,
)

@Composable
private fun buttonStyleFor(variant: ButtonVariant, enabled: Boolean = true): ButtonStyle {
    val c = Guardia.colors
    // A disabled button is a *different button*, not a faded one. The old treatment was
    // .alpha(0.5f) over the whole node, which made the fill itself translucent — a primary button
    // went half see-through with the aurora drifting underneath, so it read as a rendering glitch
    // rather than as unavailable, and the label's contrast depended on whatever happened to be
    // behind it at the time. A solid muted container with muted content is what Material does and
    // what the eye reads as "off": opaque, quiet, obviously not for pressing.
    if (!enabled) {
        return ButtonStyle(
            container = if (variant == ButtonVariant.Ghost || variant == ButtonVariant.Link) {
                Color.Transparent
            } else {
                c.muted
            },
            brush = null,
            content = c.mutedForeground,
            border = if (variant == ButtonVariant.Outline) c.border else null,
            pressedOverlay = Color.Transparent,
            glow = null,
        )
    }
    // On press, shadcn darkens the fill (`hover:bg-primary/90`). The equivalent here is a scrim of
    // the opposite luminance so the same code works in both themes.
    val scrim = if (c.isDark) Color.Black.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f)
    return when (variant) {
        // The one gradient in the component set. A primary button is the thing the screen wants you
        // to press, and giving it the brand ramp plus a halo is what separates it from "a button".
        ButtonVariant.Default -> ButtonStyle(
            container = c.brand,
            brush = Brush.horizontalGradient(c.gradientBrandAction),
            content = c.brandForeground,
            border = null,
            pressedOverlay = scrim,
            glow = c.brandGlow,
        )
        ButtonVariant.Secondary -> ButtonStyle(c.secondary, null, c.secondaryForeground, null, scrim, null)
        ButtonVariant.Outline -> ButtonStyle(Color.Transparent, null, c.foreground, c.input, c.accent, null)
        ButtonVariant.Ghost -> ButtonStyle(Color.Transparent, null, c.foreground, null, c.accent, null)
        ButtonVariant.Destructive -> ButtonStyle(
            container = c.destructive,
            brush = Brush.horizontalGradient(c.gradientDangerAction),
            content = Color.White,
            border = null,
            pressedOverlay = scrim,
            glow = c.destructive,
        )
        ButtonVariant.Link -> ButtonStyle(Color.Transparent, null, c.foreground, null, Color.Transparent, null)
    }
}

private fun ButtonSize.height(): Dp = when (this) {
    ButtonSize.Sm -> 34.dp
    ButtonSize.Default -> 40.dp
    ButtonSize.Lg -> 46.dp
    ButtonSize.Icon -> 40.dp
}

private fun ButtonSize.horizontalPadding(): Dp = when (this) {
    ButtonSize.Sm -> 12.dp
    ButtonSize.Default -> 16.dp
    ButtonSize.Lg -> 24.dp
    ButtonSize.Icon -> 0.dp
}

/**
 * shadcn `<Button>`.
 *
 * `inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm
 *  font-medium transition-colors disabled:pointer-events-none disabled:opacity-50`
 *
 * Sizes map to `h-8 px-3` / `h-9 px-4` / `h-10 px-8` / `h-9 w-9`, nudged up a few dp because a
 * 36dp target is below Android's 48dp touch minimum — the visual proportions are preserved.
 */
@Composable
fun ShButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val style = buttonStyleFor(variant, enabled)
    val shape = RoundedCornerShape(Radius.md)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A 2% compression on press. shadcn has no press-scale (the web has hover instead); on touch it
    // is the only affordance that confirms the tap landed, so it earns its place.
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "buttonScale",
    )
    val overlay by animateColorAsState(
        targetValue = if (pressed && enabled) style.pressedOverlay else Color.Transparent,
        animationSpec = tween(90),
        label = "buttonOverlay",
    )
    val padding = contentPadding ?: PaddingValues(horizontal = size.horizontalPadding())

    Row(
        modifier = modifier
            // The drawn button keeps its shadcn height (34/40/46dp); this only widens the area that
            // accepts the tap, to the 48dp WCAG 2.5.5 and Android both ask for. Material's own
            // Button does exactly this — replacing it with a custom Row is what lost it, and an
            // icon button at 40dp is the one most likely to be missed.
            .minimumInteractiveComponentSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (size == ButtonSize.Icon) Modifier.size(size.height()) else Modifier.height(size.height()))
            .then(
                if (style.glow != null && enabled)
                    Modifier.glow(style.glow, shape, radius = 16.dp, alpha = 0.5f)
                else Modifier,
            )
            .clip(shape)
            .then(if (style.brush != null) Modifier.background(style.brush) else Modifier.background(style.container))
            .background(overlay)
            .then(if (style.border != null) Modifier.border(BorderStroke(1.dp, style.border), shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textStyle = when (size) {
            ButtonSize.Sm -> MaterialTheme.typography.labelMedium
            ButtonSize.Lg -> MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
            else -> MaterialTheme.typography.labelLarge
        }.let {
            if (variant == ButtonVariant.Link) it.copy(textDecoration = TextDecoration.Underline) else it
        }
        CompositionLocalProvider(
            LocalContentColor provides style.content,
            LocalTextStyle provides textStyle.copy(color = style.content),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
            content()
            if (trailingIcon != null) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Convenience overload for the common "button with a text label" case. */
@Composable
fun ShButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    ShButton(onClick, modifier, variant, size, enabled, leadingIcon, trailingIcon) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** shadcn `<Button size="icon" variant="ghost">` — a bare tappable glyph. */
@Composable
fun ShIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    variant: ButtonVariant = ButtonVariant.Ghost,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    ShButton(onClick, modifier, variant, ButtonSize.Icon, enabled) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = tint ?: LocalContentColor.current,
        )
    }
}

// =============================================================================================
// Card
// =============================================================================================

/**
 * shadcn `<Card>` — `rounded-xl border bg-card text-card-foreground`.
 *
 * Structurally it is still shadcn's card: 12dp corners, a 1dp border, and separation that comes
 * from the hairline and the gap between cards rather than from a drop shadow under each one. That
 * is what lets a page hold ten of them without any of them shouting.
 *
 * Two things are ours. The fill is a two-stop vertical gradient and the border fades from a lit top
 * edge into the ordinary hairline, so a card reads as a pane of glass catching light from above
 * instead of a painted rectangle. And [glowColor] can put a coloured halo behind exactly one card
 * per screen — on the dashboard it is the status card, and only while the guard is running, which
 * is what makes "protected" readable across the room without reading the word.
 */
@Composable
fun ShCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    containerColor: Color? = null,
    /** Halo behind the card. Reserve it for the one surface on a screen that matters most. */
    glowColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Guardia.colors
    val shape = RoundedCornerShape(Radius.xl)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
        label = "cardScale",
    )
    val tint by animateColorAsState(
        targetValue = if (pressed && onClick != null) c.accent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(110),
        label = "cardPress",
    )
    // A two-stop fill (lighter at the top) plus a border that fades from a lit top edge into the
    // ordinary hairline. Together they read as a pane of glass catching the light from above —
    // which is what gives a flat card depth without a drop shadow under every one of them.
    val fill = remember(containerColor, c.gradientCard) {
        if (containerColor != null) SolidColor(containerColor)
        else Brush.verticalGradient(c.gradientCard)
    }
    val edge = remember(borderColor, c.borderHighlight, c.border) {
        if (borderColor != null) SolidColor(borderColor)
        else Brush.verticalGradient(listOf(c.borderHighlight, c.border, c.border))
    }
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = press; scaleY = press }
            .then(
                if (glowColor != null) Modifier.glow(glowColor, shape, radius = 26.dp, alpha = 0.30f)
                else Modifier,
            )
            .clip(shape)
            .background(fill, shape)
            .background(tint, shape)
            .border(BorderStroke(1.dp, edge), shape)
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier,
            ),
        content = content,
    )
}

/** shadcn `<CardHeader>` — `flex flex-col space-y-1.5 p-6`. */
@Composable
fun ShCardHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}

/** shadcn `<CardTitle>` — `font-semibold leading-none tracking-tight`. */
@Composable
fun ShCardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = Guardia.colors.cardForeground,
    )
}

/** shadcn `<CardDescription>` — `text-sm text-muted-foreground`. */
@Composable
fun ShCardDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = Guardia.colors.mutedForeground,
    )
}

/** shadcn `<CardContent>` — `p-6 pt-0`. */
@Composable
fun ShCardContent(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.card, end = Spacing.card, bottom = Spacing.card, top = topPadding),
        content = content,
    )
}

/** shadcn `<CardFooter>` — `flex items-center p-6 pt-0`. */
@Composable
fun ShCardFooter(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.sm),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.card, end = Spacing.card, bottom = Spacing.card),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// =============================================================================================
// Badge
// =============================================================================================

enum class BadgeVariant { Default, Secondary, Outline, Destructive, Success, Warning, Info }

/**
 * shadcn `<Badge>` — `inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs
 * font-semibold`.
 *
 * The three semantic variants beyond shadcn's four (success / warning / info) follow the same
 * construction — tinted `*Subtle` fill, `*Foreground` text, transparent border — because a security
 * app needs to say "protected", "degraded" and "off" more often than it needs to say "default".
 */
@Composable
fun ShBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
    leadingIcon: ImageVector? = null,
    mono: Boolean = false,
) {
    val c = Guardia.colors
    val (bg, fg, border) = when (variant) {
        BadgeVariant.Default -> Triple(c.primary, c.primaryForeground, Color.Transparent)
        BadgeVariant.Secondary -> Triple(c.secondary, c.secondaryForeground, Color.Transparent)
        BadgeVariant.Outline -> Triple(Color.Transparent, c.foreground, c.border)
        BadgeVariant.Destructive -> Triple(c.destructiveSubtle, c.destructiveForeground, Color.Transparent)
        BadgeVariant.Success -> Triple(c.successSubtle, c.successForeground, Color.Transparent)
        BadgeVariant.Warning -> Triple(c.warningSubtle, c.warningForeground, Color.Transparent)
        BadgeVariant.Info -> Triple(c.infoSubtle, c.infoForeground, Color.Transparent)
    }
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (border != Color.Transparent) Modifier.border(BorderStroke(1.dp, border), shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            if (mono) text.uppercase() else text,
            style = if (mono) StatusReadout else MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
        )
    }
}

/**
 * A badge with a live status dot instead of an icon — the pattern shadcn dashboards use for
 * "running / degraded / stopped". [pulsing] adds a slow breath so a *live* state is legible without
 * reading the label; it is suppressed under reduced motion.
 */
@Composable
fun ShStatusBadge(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val c = Guardia.colors
    val shape = RoundedCornerShape(Radius.md)
    val reduced = rememberReducedMotion()
    val alpha = if (pulsing && !reduced) {
        val transition = rememberInfiniteTransition(label = "statusPulse")
        val v by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "statusPulseAlpha",
        )
        v
    } else 1f
    Row(
        modifier = modifier
            .clip(shape)
            .background(c.muted)
            .border(BorderStroke(1.dp, c.border), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .then(if (pulsing) Modifier.glow(dotColor, CircleShape, radius = 8.dp, alpha = 0.9f) else Modifier)
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text.uppercase(),
            style = StatusReadout,
            color = if (pulsing) dotColor else c.mutedForeground,
            maxLines = 1,
        )
    }
}

// =============================================================================================
// Separator
// =============================================================================================

/** shadcn `<Separator>` — `shrink-0 bg-border h-[1px] w-full`. */
@Composable
fun ShSeparator(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    color: Color? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(color ?: Guardia.colors.border),
    )
}

// =============================================================================================
// Label + Input
// =============================================================================================

/** shadcn `<Label>` — `text-sm font-medium leading-none`. */
@Composable
fun ShLabel(text: String, modifier: Modifier = Modifier, required: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Guardia.colors.foreground)
        if (required) {
            Text(" *", style = MaterialTheme.typography.titleSmall, color = Guardia.colors.destructive)
        }
    }
}

/**
 * shadcn `<Input>` — `h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm
 * shadow-sm placeholder:text-muted-foreground focus-visible:ring-1 focus-visible:ring-ring`.
 *
 * Built on BasicTextField rather than Material's TextField because the Material one insists on a
 * container fill, an indicator line and a floating label, none of which exist in shadcn.
 */
@Composable
fun ShInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    /**
     * What is wrong, for a screen reader. [isError] on its own is a red border and nothing else —
     * a user who cannot see the border is told nothing at all, and WCAG 1.4.1 asks that colour
     * never be the only carrier of meaning. Falls back to a generic phrase so a caller that only
     * sets [isError] still announces *something*.
     */
    errorMessage: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 40.dp,
    leadingIcon: ImageVector? = null,
    /** Composable slots, for callers coming from a Material `TextField` signature. */
    leadingContent: (@Composable () -> Unit)? = null,
    placeholderContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
) {
    val c = Guardia.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radius.md)
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> c.destructive
            focused -> c.ring
            else -> c.input
        },
        animationSpec = tween(140),
        label = "inputBorder",
    )
    // shadcn's focus treatment is a ring *outside* the border; a 2dp border reads the same on a
    // phone and costs one draw instead of two.
    val borderWidth by animateDpAsState(if (focused || isError) 2.dp else 1.dp, tween(140), label = "inputBorderWidth")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (enabled) Color.Transparent else c.muted)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .then(
                if (isError) {
                    Modifier.semantics { error(errorMessage ?: "Invalid entry") }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .alpha(if (enabled) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.sm))
        } else if (leadingContent != null) {
            CompositionLocalProvider(LocalContentColor provides c.mutedForeground) { leadingContent() }
            Spacer(Modifier.width(Spacing.sm))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                if (placeholder != null) {
                    Text(
                        placeholder,
                        style = textStyle ?: MaterialTheme.typography.bodyMedium,
                        color = c.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (placeholderContent != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides c.mutedForeground,
                        LocalTextStyle provides (textStyle ?: MaterialTheme.typography.bodyMedium)
                            .copy(color = c.mutedForeground),
                    ) { placeholderContent() }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = (textStyle ?: MaterialTheme.typography.bodyMedium).copy(color = c.foreground),
                cursorBrush = SolidColor(c.foreground),
                interactionSource = interaction,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(Spacing.sm))
            CompositionLocalProvider(LocalContentColor provides c.mutedForeground) { trailingContent() }
        }
    }
}

// =============================================================================================
// Alert
// =============================================================================================

enum class AlertVariant { Default, Destructive, Success, Warning, Info }

/**
 * shadcn `<Alert>` — `relative w-full rounded-lg border p-4` with the icon absolutely positioned
 * at the left and the text inset past it.
 */
@Composable
fun ShAlert(
    title: String?,
    description: String?,
    modifier: Modifier = Modifier,
    variant: AlertVariant = AlertVariant.Default,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
    /**
     * When set, a close affordance appears in the corner. Give it to any alert the user could
     * reasonably want gone — an alert with no way out stops being read and starts being scenery.
     */
    onDismiss: (() -> Unit)? = null,
) {
    val c = Guardia.colors
    val (fg, border, bg) = when (variant) {
        AlertVariant.Default -> Triple(c.foreground, c.border, c.card)
        AlertVariant.Destructive -> Triple(c.destructiveForeground, c.destructive.copy(alpha = 0.4f), c.destructiveSubtle)
        AlertVariant.Success -> Triple(c.successForeground, c.success.copy(alpha = 0.4f), c.successSubtle)
        AlertVariant.Warning -> Triple(c.warningForeground, c.warning.copy(alpha = 0.4f), c.warningSubtle)
        AlertVariant.Info -> Triple(c.infoForeground, c.info.copy(alpha = 0.4f), c.infoSubtle)
    }
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, border), shape)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = fg)
            }
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    // shadcn dims the description relative to the title within a colored alert.
                    color = if (variant == AlertVariant.Default) c.mutedForeground else fg.copy(alpha = 0.85f),
                )
            }
            if (action != null) {
                Spacer(Modifier.height(Spacing.sm))
                action()
            }
        }
        if (onDismiss != null) {
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = fg.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// =============================================================================================
// Tabs
// =============================================================================================

/**
 * shadcn `<TabsList>` / `<TabsTrigger>` — a muted pill track with the active trigger raised onto
 * the background color: `bg-muted p-1 rounded-lg`, active `bg-background text-foreground shadow`.
 */
@Composable
fun ShTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Guardia.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.lg))
            .background(c.muted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) c.background else Color.Transparent,
                tween(180),
                label = "tabBg",
            )
            val fg by animateColorAsState(
                if (selected) c.brand else c.mutedForeground,
                tween(180),
                label = "tabFg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// =============================================================================================
// Progress + Skeleton
// =============================================================================================

/** shadcn `<Progress>` — `h-2 w-full rounded-full bg-primary/20`, indicator `bg-primary`. */
@Composable
fun ShProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color? = null,
    trackColor: Color? = null,
) {
    val c = Guardia.colors
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(420),
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor ?: c.muted),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(
                    if (color != null) SolidColor(color) else Brush.horizontalGradient(c.gradientBrand),
                    CircleShape,
                ),
        )
    }
}

/** shadcn `<Skeleton>` — `animate-pulse rounded-md bg-primary/10`. Static under reduced motion. */
@Composable
fun ShSkeleton(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(Radius.md),
) {
    val c = Guardia.colors
    Box(
        modifier
            .clip(shape)
            .background(c.foreground.copy(alpha = 0.07f))
            .shimmer(),
    )
}

// =============================================================================================
// Avatar
// =============================================================================================

/**
 * shadcn `<Avatar>` — `relative h-10 w-10 shrink-0 overflow-hidden rounded-full`, with
 * `<AvatarFallback>` = `bg-muted` plus initials.
 *
 * [ring] is the one addition: a 2dp outline used to say trusted / blocked at a glance in lists.
 */
@Composable
fun ShAvatar(
    name: String,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ring: Color? = null,
) {
    val c = Guardia.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(c.muted)
                .then(if (ring != null) Modifier.border(BorderStroke(2.dp, ring), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (photoPath != null) {
                coil.compose.AsyncImage(
                    model = java.io.File(photoPath),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(CircleShape),
                )
            } else {
                Text(
                    name.trim().take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = c.mutedForeground,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// =============================================================================================
// Misc primitives used across the app
// =============================================================================================

/**
 * A square, softly-tinted icon container. shadcn itself has no such component, but every shadcn
 * dashboard builds one out of `rounded-md bg-muted p-2` — this is that, factored out.
 */
@Composable
fun ShIconBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    background: Color? = null,
    size: Dp = 36.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(Radius.lg),
) {
    val c = Guardia.colors
    val fg = tint ?: c.brand
    // A tile carries its meaning in its tint, so the fill is a soft vertical ramp of that same hue
    // rather than one flat alpha — the top edge catches light like the cards do, and a row of tiles
    // in different colours reads as a set instead of as stickers.
    val fill = remember(fg, background) {
        when {
            background != null -> SolidColor(background)
            else -> Brush.verticalGradient(listOf(fg.copy(alpha = 0.22f), fg.copy(alpha = 0.08f)))
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(fill, shape)
            .border(BorderStroke(1.dp, fg.copy(alpha = 0.18f)), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * A single metric. shadcn's dashboard card: `CardHeader` with a small muted title and an icon
 * pushed to the right, then a `text-2xl font-bold` value and a muted delta line under it.
 */
@Composable
fun ShStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    caption: String? = null,
    captionColor: Color? = null,
    /** Hue for the icon tile. Defaults to the brand; give each metric its own so a row scans fast. */
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Guardia.colors
    val hue = accent ?: c.brand
    ShCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    ShIconBox(icon, tint = hue, size = 30.dp, shape = RoundedCornerShape(Radius.lg))
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.mutedForeground,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(value, style = com.guardia.app.ui.theme.DataDisplay, color = c.foreground, maxLines = 1)
            if (caption != null) {
                Text(
                    caption,
                    style = MonoCaption,
                    color = captionColor ?: c.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Section label above a group.
 *
 * Sections are separated by space rather than by rules, which is right, but a muted 13sp line on
 * its own is easy to scroll straight past. A short brand tick to its left gives the eye something
 * to catch and ties the heading to the rest of the system — the same job a rule would do, at a
 * fraction of the visual weight.
 */
@Composable
fun ShSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Guardia.colors
    val tick = remember(c.gradientBrand) { Brush.verticalGradient(c.gradientBrand) }
    Row(
        // A section label is a heading, and saying so is what lets a screen-reader user jump
        // between sections instead of swiping through every row of a long settings page.
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.sm).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            // The brand tick is decoration; it must not become a stop of its own in the tree.
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tick)
                .clearAndSetSemantics { },
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text,
            style = com.guardia.app.ui.theme.OverlineStyle,
            color = c.foreground,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Empty state: muted icon in a `bg-muted` disc, a semibold line, and a muted explanation. */
@Composable
fun ShEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Guardia.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(c.brand.copy(alpha = 0.20f), c.brand.copy(alpha = 0.05f)),
                    ),
                )
                .border(BorderStroke(1.dp, c.brand.copy(alpha = 0.20f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.foreground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = c.mutedForeground,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.xl))
            action()
        }
    }
}

/**
 * Circular gauge for the security score. Track is `bg-muted`; the arc uses the semantic color for
 * the band the score falls in, so the number and the color always agree.
 */
@Composable
fun ShScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    diameter: Dp = 148.dp,
) {
    val c = Guardia.colors
    val animated by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "score",
    )
    val ringColor = when {
        score >= 80 -> c.success
        score >= 50 -> c.warning
        else -> c.destructive
    }
    val track = c.muted
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = com.guardia.app.ui.theme.DataDisplay.copy(fontSize = 40.sp), color = c.foreground)
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.mutedForeground)
            }
        }
    }
}

/**
 * Horizontal bar chart in shadcn's chart style: `bg-muted` track behind every bar so the axis is
 * implicit, values right-aligned in mono, no gridlines.
 */
@Composable
fun ShBarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
) {
    val c = Guardia.colors
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val fill = barColor ?: c.foreground
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        data.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.mutedForeground,
                    modifier = Modifier.width(44.dp),
                    maxLines = 1,
                )
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(c.muted),
                ) {
                    val frac = (value.toFloat() / max).coerceIn(0f, 1f)
                    if (value > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac.coerceAtLeast(0.03f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(fill),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    value.toString(),
                    style = MonoCaption,
                    color = if (value > 0) c.foreground else c.mutedForeground,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Vertical columns, for a distribution across a fixed set of buckets — the shape a reader already
 * knows how to scan for "when does this happen".
 *
 * [ShBarChart]'s horizontal rows are right for a handful of named series, but a distribution has
 * more buckets than rows fit on a phone, and the eye reads a horizon of columns far faster than a
 * stack of bars. Every column keeps its muted track even at zero, so an empty bucket is visibly a
 * measured zero rather than a missing one — which is the whole point of a distribution.
 *
 * [highlightMax] lights the tallest column in the fill colour and mutes the rest, so the answer to
 * "when" is legible without reading a single label.
 */
@Composable
fun ShColumnChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    columnHeight: Dp = 108.dp,
    highlightMax: Boolean = true,
) {
    val c = Guardia.colors
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val fill = barColor ?: c.brand
    val reduced = rememberReducedMotion()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { (label, value) ->
            val isPeak = highlightMax && value > 0 && value == max
            val target = (value.toFloat() / max).coerceIn(0f, 1f)
            // The columns grow in on first composition. With motion off the same animation runs
            // with a zero duration, so the value still arrives — it just arrives instantly.
            val grown by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = if (reduced) 0 else 520),
                label = "column",
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(columnHeight)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(c.muted),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (value > 0) {
                        Box(
                            Modifier
                                // A floor of 6%: a bucket with one event must still be visible, or
                                // the chart quietly reports it as nothing.
                                .fillMaxHeight(grown.coerceAtLeast(0.06f))
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(if (isPeak) fill else fill.copy(alpha = 0.38f)),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MonoCaption,
                    color = if (isPeak) c.foreground else c.mutedForeground,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A labelled proportion bar — one measure against its own maximum, with the reading spelled out.
 * shadcn's `<Progress>` with a caption row, used where a number needs its share made visible.
 */
@Composable
fun ShMeterRow(
    label: String,
    caption: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    trailing: String? = null,
) {
    val c = Guardia.colors
    val fill = barColor ?: c.brand
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.foreground, modifier = Modifier.weight(1f), maxLines = 1)
            trailing?.let { Text(it, style = MonoCaption, color = c.foreground) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(c.muted),
        ) {
            val frac = fraction.coerceIn(0f, 1f)
            if (frac > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(frac.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(fill),
                )
            }
        }
        Text(caption, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
    }
}

/**
 * A full-bleed modal surface matching shadcn's `<DialogContent>`:
 * `rounded-lg border bg-background p-6 shadow-lg` centered over a `bg-black/80` overlay.
 * The overlay itself is supplied by the caller's Dialog.
 */
@Composable
fun ShDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Guardia.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.xl))
            .background(c.popover)
            .border(BorderStroke(1.dp, c.border), RoundedCornerShape(Radius.xl))
            .padding(Spacing.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content,
    )
}

/** Overlay scrim matching `bg-black/80`, for custom-positioned sheets. */
@Composable
fun ShScrim(modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .then(
                if (onDismiss != null)
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** shadcn `<Switch>` — `h-5 w-9 rounded-full`, thumb `h-4 w-4`, `bg-primary` / `bg-input`. */
@Composable
fun ShSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Guardia.colors
    val trackColor by animateColorAsState(
        if (checked) c.primary else c.input,
        tween(160),
        label = "switchTrack",
    )
    val offset by animateDpAsState(
        if (checked) 18.dp else 2.dp,
        spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .then(
                if (checked && enabled)
                    Modifier.glow(c.brand, CircleShape, radius = 10.dp, alpha = 0.55f)
                else Modifier,
            )
            .size(width = 38.dp, height = 22.dp)
            .clip(CircleShape)
            .background(trackColor)
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (onCheckedChange != null)
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                    ) { onCheckedChange(!checked) }
                else Modifier,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = offset)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) c.primaryForeground else c.background),
        )
    }
}

/** shadcn `<RadioGroupItem>` — a bordered circle with a filled dot when selected. */
@Composable
fun ShRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Guardia.colors
    val borderColor by animateColorAsState(if (selected) c.primary else c.input, tween(140), label = "radioBorder")
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(BorderStroke(1.5.dp, borderColor), CircleShape)
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (onClick != null)
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    )
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val dot by animateDpAsState(if (selected) 10.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 900f), label = "radioDot")
        Box(Modifier.size(dot).clip(CircleShape).background(c.primary))
    }
}

/** shadcn `<Checkbox>` — a bordered square that fills with `bg-primary` and shows a check. */
@Composable
fun ShCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Guardia.colors
    val shape = RoundedCornerShape(Radius.sm)
    val bg by animateColorAsState(if (checked) c.primary else Color.Transparent, tween(140), label = "checkboxBg")
    val borderColor by animateColorAsState(if (checked) c.primary else c.input, tween(140), label = "checkboxBorder")
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.5.dp, borderColor), shape)
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (onCheckedChange != null)
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                    ) { onCheckedChange(!checked) }
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = c.primaryForeground,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
