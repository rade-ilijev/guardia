package com.guardia.app.ui.screens.activity

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.domain.model.GuardEvent
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.theme.Spacing

@Composable
fun ActivityScreen(
    onOpenIntruders: () -> Unit,
    onOpenStats: () -> Unit = {},
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    GuardiaScaffold(
        title = "Activity",
        actions = {
            IconButton(onClick = onOpenStats) {
                Icon(Icons.Filled.Insights, contentDescription = "Insights")
            }
            IconButton(onClick = onOpenIntruders) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Intruder photos")
            }
            if (events.isNotEmpty()) {
                IconButton(onClick = { viewModel.clear() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear")
                }
            }
        },
    ) { padding ->
        if (events.isEmpty()) {
            com.guardia.app.ui.components.EmptyState(
                icon = Icons.Filled.History,
                title = "No activity yet",
                subtitle = "Guarding events, intruder alerts, and voice triggers will appear here as a timeline.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            val grouped = events.groupBy { dayLabel(it.timestamp) }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.screen, end = Spacing.screen, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                grouped.forEach { (day, dayEvents) ->
                    item(key = day) {
                        SectionHeader(day)
                        GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                dayEvents.forEachIndexed { index, event ->
                                    val intruder = event.type == GuardEvent.Type.INTRUDER_LOCK ||
                                        event.type == GuardEvent.Type.UNKNOWN_FACE ||
                                        event.type == GuardEvent.Type.WRONG_UNLOCK
                                    ListItem(
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = if (intruder) Modifier.clickable { onOpenIntruders() } else Modifier,
                                        leadingContent = {
                                            Icon(
                                                iconFor(event.type),
                                                contentDescription = null,
                                                tint = colorFor(event.type),
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(colorFor(event.type).copy(alpha = 0.12f))
                                                    .padding(8.dp),
                                            )
                                        },
                                        headlineContent = { Text(event.message) },
                                        supportingContent = {
                                            Text(DateUtils.getRelativeTimeSpanString(event.timestamp).toString())
                                        },
                                        trailingContent = event.photoPath?.let { path ->
                                            { EventThumbnail(path, viewModel) }
                                        },
                                    )
                                    if (index < dayEvents.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.padding(start = 64.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventThumbnail(path: String, viewModel: ActivityViewModel) {
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = viewModel.loadThumbnail(path)
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(46.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                it,
                contentDescription = "Captured photo",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

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

@Composable
private fun iconFor(type: GuardEvent.Type): ImageVector = when (type) {
    GuardEvent.Type.GUARDING_STARTED -> Icons.Filled.CheckCircle
    GuardEvent.Type.GUARDING_STOPPED -> Icons.Filled.Info
    GuardEvent.Type.INTRUDER_LOCK, GuardEvent.Type.WRONG_UNLOCK -> Icons.Filled.Lock
    GuardEvent.Type.UNKNOWN_FACE -> Icons.Filled.Warning
    GuardEvent.Type.ENROLLMENT -> Icons.Filled.PersonAdd
    GuardEvent.Type.INFO -> Icons.Filled.Info
}

@Composable
private fun colorFor(type: GuardEvent.Type) = when (type) {
    GuardEvent.Type.INTRUDER_LOCK, GuardEvent.Type.WRONG_UNLOCK, GuardEvent.Type.UNKNOWN_FACE -> MaterialTheme.colorScheme.error
    GuardEvent.Type.GUARDING_STARTED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
