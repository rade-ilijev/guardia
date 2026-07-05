package com.guardia.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.guardia.app.ui.theme.Spacing

/**
 * App-wide ambient background: a deep vertical gradient with a barely-there engineering grid and
 * two soft brand glows (top-center and bottom-corner), giving every screen the depth of a security
 * console instead of a flat fill. All static — one Canvas pass, no animation cost.
 */
@Composable
fun GuardiaBackdrop(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val gridColor = scheme.primary.copy(alpha = 0.035f)
    val glowTop = scheme.primary.copy(alpha = 0.07f)
    val glowCorner = scheme.primary.copy(alpha = 0.045f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to scheme.surfaceContainerLow,
                    0.32f to scheme.background,
                    1f to scheme.background,
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Engineering grid: fine lines every 44dp, fading out toward the bottom.
            val step = 44.dp.toPx()
            val strokeW = 1f
            var x = step
            while (x < size.width) {
                drawLine(
                    brush = Brush.verticalGradient(listOf(gridColor, Color.Transparent), endY = size.height * 0.85f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeW,
                )
                x += step
            }
            var y = step
            while (y < size.height) {
                val fade = 1f - (y / size.height) * 0.8f
                drawLine(
                    color = gridColor.copy(alpha = gridColor.alpha * fade),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeW,
                )
                y += step
            }
            // Top-center brand glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowTop, Color.Transparent),
                    center = Offset(size.width / 2f, -size.width * 0.15f),
                    radius = size.width * 0.85f,
                ),
                center = Offset(size.width / 2f, -size.width * 0.15f),
                radius = size.width * 0.85f,
            )
            // Bottom-right counter-glow for depth.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowCorner, Color.Transparent),
                    center = Offset(size.width * 1.05f, size.height * 1.02f),
                    radius = size.width * 0.7f,
                ),
                center = Offset(size.width * 1.05f, size.height * 1.02f),
                radius = size.width * 0.7f,
            )
        }
    }
}

/** App-wide screen scaffold with a consistent top bar, ambient backdrop, and optional back button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardiaScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Note: the ambient GuardiaBackdrop is drawn once at the root (GuardiaRoot) behind all screens.
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            containerColor = Color.Transparent,
            content = content,
        )
    }
}

/** Vertically scrolling settings body with a grouped layout. */
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
            .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) { content() }
}

/** A titled group of rows rendered as a single card with dividers between rows. */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column {
        if (title != null) SectionHeader(title)
        GuardiaCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = Spacing.lg),
    )
}

@Composable
private fun RowScaffold(
    title: String,
    subtitle: String?,
    leading: ImageVector?,
    enabled: Boolean,
    premium: Boolean,
    onClick: (() -> Unit)?,
    leadingTint: androidx.compose.ui.graphics.Color? = null,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    val tint = (leadingTint ?: MaterialTheme.colorScheme.primary).copy(alpha = alpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null && enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(
                leading,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f * alpha))
                    .padding(8.dp),
            )
            Spacer(Modifier.width(Spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                if (premium) {
                    Spacer(Modifier.width(Spacing.sm))
                    PremiumBadge()
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
    leadingTint: androidx.compose.ui.graphics.Color? = null,
) {
    RowScaffold(title, subtitle, leading, enabled, premium, onClick = onClick, leadingTint = leadingTint) {
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(Spacing.xs))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
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
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

enum class BannerTone { Info, Warning, Success, Danger }

@Composable
fun InfoBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
) {
    val (bg, fg) = when (tone) {
        BannerTone.Info -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        BannerTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BannerTone.Success -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BannerTone.Danger -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg)
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("PRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
    }
}

/** Circular progress ring used for the security score. [score] is 0..100. */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val animated by animateFloatAsState(targetValue = score / 100f, animationSpec = tween(800), label = "score")
    val ringColor = when {
        score >= 80 -> MaterialTheme.colorScheme.primary
        score >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ringColor)
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Animated placeholder rows shown while a list is loading. */
@Composable
fun SkeletonRows(count: Int = 5) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(750),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "shimmer",
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * alpha)
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(count) { i ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(base))
                    Spacer(Modifier.width(Spacing.md))
                    Box(
                        Modifier
                            .height(14.dp)
                            .fillMaxWidth(0.55f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .background(base),
                    )
                }
                if (i < count - 1) RowDivider()
            }
        }
    }
}

/** Simple labeled vertical bar chart. [data] is a list of (label, value). */
@Composable
fun BarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { (label, value) ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (value > 0) barColor else onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                val frac = (value.toFloat() / max).coerceIn(0.02f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .fillMaxHeight(frac)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (value > 0) barColor else onSurfaceVariant.copy(alpha = 0.15f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun PrimaryButtonBar(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { content() }
    }
}
