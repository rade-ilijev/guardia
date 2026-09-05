package com.guardia.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.MonoCaption
import com.guardia.app.ui.theme.Spacing

/*
 * Screen chrome and settings rows, rebuilt on the shadcn primitives.
 */

/**
 * App background: the page colour with three wide, slowly drifting fields of brand light over it.
 *
 * Drawn once at the root, behind every screen, which is why [GuardiaScaffold] and the screens
 * themselves keep transparent containers. See [AuroraBackdrop] for how it stays cheap.
 */
@Composable
fun GuardiaBackdrop(modifier: Modifier = Modifier) {
    AuroraBackdrop(modifier)
}

/**
 * Standard screen frame.
 *
 * The header used to be a near-opaque slab with a hairline rule under it. Two things were wrong
 * with that. It cut the ambient backdrop off at exactly the point the eye lands first, so every
 * screen opened on a dark band; and the rule turned the title into a separate piece of chrome
 * bolted above the page rather than part of it.
 *
 * So the header is transparent, and legibility comes from a *scrim* instead: a vertical gradient
 * that is solid behind the status bar and has faded to nothing by the bottom of the title row.
 * Content scrolling underneath dissolves into the background instead of colliding with an edge,
 * and the aurora carries straight through the top of the screen.
 */
@Composable
fun GuardiaScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Guardia.colors
    val scrim = remember(c.background) {
        Brush.verticalGradient(
            0.0f to c.background,
            0.45f to c.background.copy(alpha = 0.88f),
            1.0f to Color.Transparent,
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(scrim)
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        GlassIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onBack,
                            contentDescription = "Back",
                        )
                        Spacer(Modifier.width(Spacing.md))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = c.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.mutedForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    actions()
                }
                // A short continuation of the scrim below the row, so the fade finishes past the
                // title rather than at it — otherwise the first card can appear to touch the text.
                Box(Modifier.fillMaxWidth().height(Spacing.md))
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        // Transparent so the root aurora shows through every screen.
        containerColor = Color.Transparent,
        contentColor = c.foreground,
        content = content,
    )
}

/**
 * A round, translucent icon button for use on top of the backdrop — the back affordance and any
 * header action.
 *
 * A bare ghost icon disappears against the aurora, and a filled button is far too loud for a back
 * arrow. This is the middle: a disc of the card colour at partial opacity with a hairline edge, so
 * it reads as a control sitting on the page rather than a hole punched in it.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null,
) {
    val c = Guardia.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "glassIconScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(40.dp)
            .clip(CircleShape)
            .background(c.card.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, c.border), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint ?: c.foreground,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Scrolling settings body: page gutter, section rhythm, and room for the floating tab bar. */
@Composable
fun SettingsColumn(
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.section),
    ) { content() }
}

/** A titled group of rows rendered as one card with separators between rows. */
@Composable
fun SettingsGroup(
    title: String? = null,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column {
        if (title != null) SectionHeader(title)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Guardia.colors.mutedForeground,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }
        ShCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** Separator between rows inside a settings card, inset past the leading icon. */
@Composable
fun RowDivider() {
    ShSeparator(startIndent = Spacing.lg)
}

/**
 * The shared geometry of every settings row: optional leading icon box, title + subtitle stack,
 * trailing control. shadcn list rows are `py-3` with `text-sm` titles and
 * `text-xs text-muted-foreground` subtitles — dense, and readable because of the muted second line
 * rather than because of size.
 */
@Composable
private fun RowScaffold(
    title: String,
    subtitle: String?,
    leading: ImageVector?,
    enabled: Boolean,
    premium: Boolean,
    onClick: (() -> Unit)?,
    leadingTint: Color? = null,
    trailing: @Composable () -> Unit,
) {
    val c = Guardia.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled)
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                else Modifier,
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            ShIconBox(icon = leading, tint = leadingTint, size = 34.dp)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                if (premium) {
                    Spacer(Modifier.width(Spacing.sm))
                    PremiumBadge()
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
            }
        }
        Spacer(Modifier.width(Spacing.md))
        trailing()
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    premium: Boolean = false,
) {
    RowScaffold(title, subtitle, leading, enabled, premium, onClick = { onCheckedChange(!checked) }) {
        ShSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: ImageVector? = null,
    value: String? = null,
    enabled: Boolean = true,
    premium: Boolean = false,
    leadingTint: Color? = null,
) {
    val c = Guardia.colors
    RowScaffold(title, subtitle, leading, enabled, premium, onClick = onClick, leadingTint = leadingTint) {
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = c.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.mutedForeground,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    RowScaffold(label, subtitle, leading = null, enabled = enabled, premium = false, onClick = onClick) {
        ShRadio(selected = selected, onClick = null, enabled = enabled)
    }
}

@Composable
fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    subtitle: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = Guardia.colors
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.foreground, modifier = Modifier.weight(1f))
            // The live value reads as data, so it gets the mono face — shadcn's `tabular-nums`.
            Text(valueLabel, style = MonoCaption, color = c.foreground)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.muted,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
        }
    }
}

/** Tone of an inline banner. Maps 1:1 onto shadcn's alert variants. */
enum class BannerTone { Info, Warning, Success, Danger }

/** Inline banner — a shadcn `<Alert>` with only a description. */
@Composable
fun InfoBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
) {
    ShAlert(
        title = null,
        description = text,
        modifier = modifier,
        icon = icon,
        variant = when (tone) {
            BannerTone.Info -> AlertVariant.Info
            BannerTone.Warning -> AlertVariant.Warning
            BannerTone.Success -> AlertVariant.Success
            BannerTone.Danger -> AlertVariant.Destructive
        },
    )
}

/**
 * Pro marker.
 *
 * Guardia is shipping with every feature unlocked for everyone while it is in testing, so this
 * renders nothing. The `premium = true` markers are left on the settings rows and section headers
 * they belong to, and [com.guardia.app.core.billing.EntitlementManager.allFeaturesUnlocked] is the
 * single switch that gates behaviour — so reintroducing the paid tier later means restoring this
 * body and flipping that flag, not re-annotating the app.
 */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) = Unit

/** Circular gauge for the security score. */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    ShScoreRing(score = score, modifier = modifier, label = label)
}

/** Loading placeholder rows inside a card. */
@Composable
fun SkeletonRows(count: Int = 5) {
    ShCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            repeat(count) { i ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShSkeleton(
                        Modifier.size(34.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShSkeleton(Modifier.height(12.dp).fillMaxWidth(0.5f))
                        ShSkeleton(Modifier.height(10.dp).fillMaxWidth(0.3f))
                    }
                }
                if (i < count - 1) RowDivider()
            }
        }
    }
}

/** Labelled bar chart. */
@Composable
fun BarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
) {
    ShBarChart(data = data, modifier = modifier, barColor = barColor)
}

/**
 * Pinned action bar at the bottom of a flow. shadcn's sticky footer is `border-t bg-background`,
 * separated by a rule rather than lifted by a shadow.
 */
@Composable
fun PrimaryButtonBar(content: @Composable () -> Unit) {
    val c = Guardia.colors
    Column(Modifier.fillMaxWidth().background(c.background.copy(alpha = 0.94f))) {
        ShSeparator()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { content() }
    }
}
