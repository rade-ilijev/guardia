package com.guardia.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.OverlineStyle
import com.guardia.app.ui.theme.Spacing

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, premium: Boolean = false) {
    Row(
        modifier = modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = OverlineStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        if (premium) {
            Spacer(Modifier.width(Spacing.sm))
            PremiumBadge()
        }
    }
}

/**
 * The app's standard surface: a refined "console panel" with a low-contrast top-down fill, a soft
 * drop shadow for depth, and a barely-there hairline edge (a faint light highlight up top fading to
 * a darker outline at the bottom). When [onClick] is set the whole card gets a subtle press-scale.
 */
@Composable
fun GuardiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "cardScale",
    )
    val fill = Brush.verticalGradient(
        listOf(scheme.surfaceContainerHigh, scheme.surfaceContainer),
    )
    // Thin glass edge: a gentle light highlight at the top easing into a soft dark outline.
    val borderBrush = Brush.verticalGradient(
        listOf(
            scheme.onSurface.copy(alpha = 0.12f),
            scheme.outlineVariant.copy(alpha = 0.28f),
        ),
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = 10.dp, shape = shape, clip = false, spotColor = Color.Black, ambientColor = Color.Black)
            .clip(shape)
            .background(fill, shape)
            .border(BorderStroke(0.8.dp, borderBrush), shape)
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier,
            ),
    ) { content() }
}

/** Circular tinted badge holding an icon - the app's standard "icon chip". */
@Composable
fun IconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    GuardiaCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            IconChip(icon, tint = tint, size = 38.dp)
            Spacer(Modifier.height(Spacing.md))
            // Value and optional caption share one baseline so a caption doesn't add height
            // (keeps side-by-side tiles the same height).
            Row(verticalAlignment = Alignment.Bottom) {
                // Mono data display so numbers read like instrumentation and align consistently.
                Text(value, style = com.guardia.app.ui.theme.DataDisplay, color = MaterialTheme.colorScheme.onSurface)
                if (caption != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label.uppercase(),
                style = com.guardia.app.ui.theme.OverlineStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
