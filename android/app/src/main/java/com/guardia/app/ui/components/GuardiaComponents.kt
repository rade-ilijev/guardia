package com.guardia.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Spacing

/*
 * The app's original component names, re-implemented on top of the shadcn primitives in Shadcn.kt.
 *
 * Keeping the names means every screen picks up the new design system without being rewritten call
 * by call; the ones that needed a genuinely different layout (dashboard, people, settings, ...) are
 * reworked in their own files.
 */

/** Section heading above a group — shadcn's plain muted label. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, premium: Boolean = false) {
    ShSectionLabel(
        text = text,
        modifier = modifier,
        trailing = if (premium) ({ PremiumBadge() }) else null,
    )
}

/**
 * The app's standard surface. Now a shadcn `<Card>`: flat `bg-card`, one 1dp `border`, 12dp
 * corners, no shadow and no gradient.
 */
@Composable
fun GuardiaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ShCard(modifier = modifier, onClick = onClick) { content() }
}

/** Person portrait with an optional status ring — shadcn `<Avatar>` plus a meaning ring. */
@Composable
fun PersonAvatar(
    name: String,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ring: Color? = null,
) {
    ShAvatar(name = name, photoPath = photoPath, modifier = modifier, size = size, ring = ring)
}

/**
 * Tinted icon container. Square with 8dp corners rather than the old circle — shadcn keeps circles
 * for avatars and status dots only, so a squared chip reads as "thing" and a circle reads as
 * "person".
 */
@Composable
fun IconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 36.dp,
) {
    ShIconBox(icon = icon, modifier = modifier, tint = tint, size = size)
}

/** Single metric tile — shadcn's dashboard stat card. */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    ShStatCard(
        label = label,
        value = value,
        modifier = modifier,
        icon = icon,
        caption = caption,
        captionColor = tint,
        onClick = onClick,
    )
}

/** Empty-list placeholder. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    ShEmptyState(icon = icon, title = title, description = subtitle, modifier = modifier)
}

/**
 * A labelled key/value line, the shape shadcn uses inside a CardContent for detail panes:
 * muted label on the left, foreground value right-aligned.
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    mono: Boolean = false,
) {
    val c = Guardia.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.mutedForeground,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.md))
        androidx.compose.material3.Text(
            value,
            style = if (mono) com.guardia.app.ui.theme.MonoCaption else MaterialTheme.typography.titleSmall,
            color = valueColor ?: c.foreground,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/**
 * A thin colored strip keying a card to a status without filling it — shadcn's `border-l-4` accent
 * on an otherwise neutral card. Drop it as the first child of a Row inside a [ShCard].
 */
@Composable
fun AccentEdge(color: Color, modifier: Modifier = Modifier, width: Dp = 3.dp) {
    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(color),
    )
}
