package com.guardia.app.ui.screens.activity

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.domain.model.GuardEvent
import com.guardia.app.ui.components.EmptyState
import com.guardia.app.ui.components.GlassIconButton
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.ShBadge
import com.guardia.app.ui.components.ShTabs
import com.guardia.app.ui.components.BadgeVariant
import com.guardia.app.ui.components.animateEntrance
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.MonoCaption
import com.guardia.app.ui.theme.OverlineStyle
import com.guardia.app.ui.theme.Radius
import com.guardia.app.ui.theme.Spacing

/**
 * Activity — the security log.
 *
 * Drawn as a timeline rather than a list, which is what it always was in substance: a rail down the
 * left, one node per event carrying its type, exact times in a monospaced column, and the evidence
 * thumbnail sitting on the row that produced it. The previous version leaned on Material 3's
 * `ListItem` — the only screen in the app that still did — so it arrived with Material's own
 * padding, type scale and surface colours and read as a different app from every screen around it.
 *
 * The rail is what a log wants and a list cannot give: continuity. Events belong to a sequence, and
 * a line joining them says that far more directly than repeated card edges pulling them apart.
 */
@Composable
fun ActivityScreen(
    onOpenIntruders: () -> Unit,
    onOpenStats: () -> Unit = {},
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var filter by remember { mutableIntStateOf(0) }

    // Only animate items composed while the screen is arriving; an item scrolled back into view is
    // a fresh composition, and without this the list would re-play its entrance on every scroll up.
    val enteredAt = remember { android.os.SystemClock.uptimeMillis() }
    fun arriving() = android.os.SystemClock.uptimeMillis() - enteredAt < 700L

    val shown = remember(events, filter) {
        when (filter) {
            1 -> events.filter { it.type.isAlert }
            2 -> events.filter { !it.type.isAlert }
            else -> events
        }
    }

    GuardiaScaffold(
        title = "Activity",
        actions = {
            GlassIconButton(Icons.Filled.Insights, onOpenStats, contentDescription = "Insights")
            Spacer(Modifier.width(Spacing.sm))
            GlassIconButton(
                Icons.Filled.PhotoLibrary,
                onOpenIntruders,
                contentDescription = "Intruder photos",
            )
            if (events.isNotEmpty()) {
                Spacer(Modifier.width(Spacing.sm))
                GlassIconButton(
                    Icons.Filled.DeleteSweep,
                    onClick = { viewModel.clear() },
                    contentDescription = "Clear log",
                    tint = Guardia.colors.destructive,
                )
            }
        },
    ) { padding ->
        if (events.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "No activity yet",
                subtitle = "Guarding events, intruder alerts and recognitions appear here as a timeline.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@GuardiaScaffold
        }

        val grouped = remember(shown) { shown.groupBy { dayLabel(it.timestamp) } }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                top = Spacing.sm,
                end = Spacing.screen,
                bottom = Spacing.bottomBarClearance,
            ),
        ) {
            // A log is mostly routine with a few moments that matter, so the first thing it owes
            // the reader is a way to see only those. The counts are on the tabs because "Alerts 0"
            // is itself the answer on a good week.
            item(key = "filter") {
                val alerts = events.count { it.type.isAlert }
                ShTabs(
                    tabs = listOf(
                        "All ${events.size}",
                        "Alerts $alerts",
                        "System ${events.size - alerts}",
                    ),
                    selectedIndex = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.fillMaxWidth().animateEntrance(0, arriving()),
                )
                Spacer(Modifier.height(Spacing.lg))
            }

            if (shown.isEmpty()) {
                item(key = "empty-filter") {
                    Text(
                        if (filter == 1) "Nothing has tripped the guard in this log."
                        else "No routine events in this log.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Guardia.colors.mutedForeground,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                }
            }

            grouped.entries.forEachIndexed { groupIndex, (day, dayEvents) ->
                item(key = "h-$day") {
                    DayHeader(
                        day = day,
                        count = dayEvents.size,
                        alerts = dayEvents.count { it.type.isAlert },
                        modifier = Modifier
                            .padding(top = if (groupIndex == 0) 0.dp else Spacing.xl)
                            .animateEntrance(groupIndex + 1, arriving()),
                    )
                }
                itemsIndexed(dayEvents, key = { _, e -> e.id }) { index, event ->
                    TimelineRow(
                        event = event,
                        first = index == 0,
                        last = index == dayEvents.lastIndex,
                        onClick = if (event.type.isAlert) onOpenIntruders else null,
                        thumbnail = event.photoPath?.let { path ->
                            { EventThumbnail(path, viewModel, alert = event.type.isAlert) }
                        },
                    )
                }
            }
        }
    }
}

/** The day, with the day's weight beside it: total events, and how many of them mattered. */
@Composable
private fun DayHeader(day: String, count: Int, alerts: Int, modifier: Modifier = Modifier) {
    val c = Guardia.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(day.uppercase(), style = OverlineStyle, color = c.foreground)
        Spacer(Modifier.width(Spacing.md))
        Box(Modifier.weight(1f).height(1.dp).background(c.border))
        Spacer(Modifier.width(Spacing.md))
        if (alerts > 0) {
            ShBadge(
                text = if (alerts == 1) "1 alert" else "$alerts alerts",
                variant = BadgeVariant.Destructive,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text("$count", style = MonoCaption, color = c.mutedForeground)
    }
}

/**
 * One event on the rail.
 *
 * The line is drawn behind the whole row rather than assembled from spacers, so it reaches the
 * row's real height whatever the message wraps to — and it is drawn in two segments with a gap
 * where the node sits, so the node reads as sitting *on* the line rather than over it. [first] and
 * [last] trim the ends, which is what stops a day's rail dangling into the header above it or the
 * blank space below.
 */
@Composable
private fun TimelineRow(
    event: GuardEvent,
    first: Boolean,
    last: Boolean,
    onClick: (() -> Unit)?,
    thumbnail: (@Composable () -> Unit)?,
) {
    val c = Guardia.colors
    val alert = event.type.isAlert
    val tint = colorFor(event.type)
    val density = LocalDensity.current
    val railX = with(density) { NodeSize.toPx() / 2f }
    val gapTop = with(density) { NodeTopPadding.toPx() }
    val gapBottom = with(density) { (NodeTopPadding + NodeSize).toPx() }
    val lineColor = c.border

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                if (!first) {
                    drawLine(lineColor, Offset(railX, 0f), Offset(railX, gapTop), stroke)
                }
                if (!last) {
                    drawLine(lineColor, Offset(railX, gapBottom), Offset(railX, size.height), stroke)
                }
            }
            .clip(RoundedCornerShape(Radius.lg))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(end = Spacing.sm, bottom = Spacing.lg),
    ) {
        // The node carries the event's type and, by its fill, its severity: an alert is solid on a
        // soft halo, everything routine is a quiet ring. Colour alone does that work, so the rows
        // themselves need no red backgrounds to shout with.
        Box(
            modifier = Modifier
                .padding(top = NodeTopPadding)
                .size(NodeSize)
                .clip(CircleShape)
                .background(if (alert) tint.copy(alpha = 0.18f) else c.muted)
                .border(1.dp, if (alert) tint.copy(alpha = 0.55f) else c.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconFor(event.type),
                contentDescription = null,
                tint = if (alert) tint else c.mutedForeground,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Exact time in mono, in its own column: a security log is read by scanning down
                // the times, and proportional digits make that column ragged.
                Text(timeLabel(event.timestamp), style = MonoCaption, color = c.mutedForeground)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alert) c.foreground else c.mutedForeground,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                DateUtils.getRelativeTimeSpanString(
                    event.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = c.mutedForeground,
            )
        }
        if (thumbnail != null) {
            Spacer(Modifier.width(Spacing.md))
            Box(Modifier.padding(top = 2.dp)) { thumbnail() }
        }
    }
}

private val NodeSize = 30.dp
private val NodeTopPadding = 2.dp

@Composable
private fun EventThumbnail(path: String, viewModel: ActivityViewModel, alert: Boolean = false) {
    val c = Guardia.colors
    val thumbPx = with(LocalDensity.current) { 46.dp.roundToPx() }
    val bitmap by androidx.compose.runtime.produceState<ImageBitmap?>(null, path) {
        value = viewModel.loadThumbnail(path, thumbPx)
    }
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(shape)
            .background(c.muted)
            .border(1.dp, if (alert) c.destructive.copy(alpha = 0.5f) else c.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it,
                contentDescription = "Captured photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = c.mutedForeground,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The three event types that mean someone other than the owner was at the phone. */
private val GuardEvent.Type.isAlert: Boolean
    get() = this == GuardEvent.Type.INTRUDER_LOCK ||
        this == GuardEvent.Type.UNKNOWN_FACE ||
        this == GuardEvent.Type.WRONG_UNLOCK

private fun timeLabel(timestamp: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun dayLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val dayMs = 24 * 60 * 60 * 1000L
    val startOfToday = now - (now % dayMs)
    return when {
        timestamp >= startOfToday -> "Today"
        timestamp >= startOfToday - dayMs -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

private fun iconFor(type: GuardEvent.Type): ImageVector = when (type) {
    GuardEvent.Type.GUARDING_STARTED -> Icons.Filled.CheckCircle
    GuardEvent.Type.GUARDING_STOPPED -> Icons.Filled.Info
    GuardEvent.Type.INTRUDER_LOCK, GuardEvent.Type.WRONG_UNLOCK -> Icons.Filled.Lock
    GuardEvent.Type.UNKNOWN_FACE -> Icons.Filled.Warning
    GuardEvent.Type.ENROLLMENT -> Icons.Filled.PersonAdd
    GuardEvent.Type.FALSE_LOCK -> Icons.Filled.HowToReg
    GuardEvent.Type.INFO -> Icons.Filled.Info
}

@Composable
private fun colorFor(type: GuardEvent.Type): Color = when (type) {
    GuardEvent.Type.INTRUDER_LOCK, GuardEvent.Type.WRONG_UNLOCK, GuardEvent.Type.UNKNOWN_FACE ->
        Guardia.colors.destructive
    GuardEvent.Type.GUARDING_STARTED -> Guardia.colors.success
    GuardEvent.Type.FALSE_LOCK -> Guardia.colors.brand
    else -> Guardia.colors.mutedForeground
}
