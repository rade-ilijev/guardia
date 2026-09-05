package com.guardia.app.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.ui.components.BarChart
import com.guardia.app.ui.components.EmptyState
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.ShColumnChart
import com.guardia.app.ui.components.ShMeterRow
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.components.StatTile
import com.guardia.app.ui.components.animateEntrance
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Insights: what guarding has actually seen.
 *
 * The screen is built around three questions a user of a security app really has, in the order they
 * ask them — *when* is my phone at risk, *is it getting worse*, and *is Guardia any good at telling
 * me apart from a stranger*. Counts alone answered none of those, so every panel here either shows
 * a distribution, a comparison against the previous period, or a rate.
 *
 * Everything is drawn from the activity log, which is capped at the most recent few hundred events.
 * The footer says so rather than letting the numbers imply they cover all time.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val c = Guardia.colors

    GuardiaScaffold(title = "Insights", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.sm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth().animateEntrance(0),
            ) {
                StatTile(
                    Icons.Filled.Warning,
                    ui.intruders7d.toString(),
                    "Attempts, 7 days",
                    Modifier.weight(1f),
                    tint = if (ui.intruders7d > 0) c.destructive else c.success,
                )
                StatTile(
                    Icons.Filled.Verified,
                    ui.totalRecognitions.toString(),
                    "Recognitions",
                    Modifier.weight(1f),
                    tint = c.success,
                )
                StatTile(
                    Icons.Filled.HowToReg,
                    if (ui.unknownFaceLocks == 0) "-" else "${(ui.falseLockRate * 100).toInt()}%",
                    "Locked you out",
                    Modifier.weight(1f),
                    tint = if (ui.falseLockRate > 0.25f) c.warning else c.mutedForeground,
                )
            }

            TrendLine(ui, modifier = Modifier.animateEntrance(1))

            if (!ui.hasIntruderHistory) {
                // Three empty charts say "no data" three times over. One panel says it once, and
                // says the right thing about it: nothing found is the outcome you wanted.
                GuardiaCard(modifier = Modifier.fillMaxWidth().animateEntrance(2)) {
                    EmptyState(
                        Icons.Filled.ShieldMoon,
                        "Nothing has tripped the guard",
                        "No unknown face, blocked person or failed unlock has been recorded yet. Patterns appear here once there is something to see.",
                    )
                }
            } else {
                SectionHeader("When attempts happen")
                GuardiaCard(modifier = Modifier.fillMaxWidth().animateEntrance(2)) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        ShColumnChart(
                            data = ui.perWindow.map { it.label to it.value },
                            barColor = c.destructive,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = c.mutedForeground,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.size(Spacing.sm))
                            Text(
                                ui.busiestWindow?.let { "Most attempts between $it" }
                                    ?: "No time of day stands out yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.mutedForeground,
                            )
                        }
                    }
                }

                SectionHeader("Last 7 days")
                GuardiaCard(modifier = Modifier.fillMaxWidth().animateEntrance(3)) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        BarChart(
                            data = ui.perDay.map { it.label to it.value },
                            barColor = c.destructive,
                        )
                    }
                }
            }

            if (ui.unknownFaceLocks > 0) {
                SectionHeader("How often it locks you out")
                GuardiaCard(modifier = Modifier.fillMaxWidth().animateEntrance(4)) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        ShMeterRow(
                            label = "Locks that were actually you",
                            caption = "${ui.falseLocks} of ${ui.unknownFaceLocks} unknown-face locks",
                            fraction = ui.falseLockRate,
                            trailing = "${(ui.falseLockRate * 100).toInt()}%",
                            barColor = if (ui.falseLockRate > 0.25f) c.warning else c.success,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            if (ui.falseLockRate > 0.25f) {
                                "Guardia is being too suspicious of you. Add a few more face samples in different light, or ease the sensitivity in Settings > Recognition."
                            } else {
                                "Low is good: Guardia is separating you from strangers cleanly. Tap \"That was me\" on the home screen whenever it gets one wrong and it learns from the photo."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.mutedForeground,
                        )
                    }
                }
            }

            SectionHeader("How well it knows your faces")
            val known = ui.people.filter { it.recognitions > 0 }
            GuardiaCard(modifier = Modifier.fillMaxWidth().animateEntrance(5)) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    if (known.isEmpty()) {
                        Text(
                            if (ui.people.isEmpty()) {
                                "Nobody is enrolled yet. Add a face in People and match quality will be tracked here."
                            } else {
                                "Guarding hasn't recognised anyone yet. Once it has, each person's average match strength appears here."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.mutedForeground,
                        )
                    } else {
                        known.forEach { person ->
                            ShMeterRow(
                                label = person.name,
                                caption = if (person.weak) {
                                    "${person.recognitions} recognitions - ${person.sampleCount} samples. Add more, in different light, to make matches sit further from the threshold."
                                } else {
                                    "${person.recognitions} recognitions from ${person.sampleCount} samples"
                                },
                                fraction = person.confidence,
                                trailing = "${(person.confidence * 100).toInt()}%",
                                barColor = if (person.weak) c.warning else c.success,
                            )
                        }
                    }
                }
            }

            if (ui.since > 0L) {
                Text(
                    "Based on activity recorded since ${formatDay(ui.since)}. Guardia keeps the most recent few hundred events.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.height(Spacing.bottomBarClearance))
        }
    }
}

/**
 * This week against last week. A count on its own cannot say whether things are getting better, and
 * "getting better" is the only reason to look at a security dashboard twice.
 */
@Composable
private fun TrendLine(ui: StatsUi, modifier: Modifier = Modifier) {
    val c = Guardia.colors
    val delta = ui.intruders7d - ui.intrudersPrev7d
    val rising = delta > 0
    val tint: Color = when {
        delta == 0 -> c.mutedForeground
        rising -> c.destructive
        else -> c.success
    }
    val text = when {
        ui.intruders7d == 0 && ui.intrudersPrev7d == 0 -> "No attempts in the last two weeks"
        delta == 0 -> "Same as the week before"
        rising -> "$delta more than the week before"
        else -> "${-delta} fewer than the week before"
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (delta != 0) {
            Icon(
                if (rising) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(Spacing.xs))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            fontWeight = if (delta == 0) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

private fun formatDay(epochMs: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
