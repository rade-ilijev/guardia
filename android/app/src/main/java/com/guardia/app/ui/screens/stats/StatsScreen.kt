package com.guardia.app.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.ui.components.BarChart
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.IconChip
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.components.StatTile
import com.guardia.app.ui.theme.Spacing

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

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
            SectionHeader("Totals")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                StatTile(Icons.Filled.Verified, ui.totalRecognitions.toString(), "Recognitions", Modifier.weight(1f))
                StatTile(
                    Icons.Filled.Warning,
                    ui.totalIntruders.toString(),
                    "Intruder events",
                    Modifier.weight(1f),
                    tint = if (ui.totalIntruders > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                StatTile(Icons.Filled.Event, ui.totalEvents.toString(), "Total events", Modifier.weight(1f), tint = MaterialTheme.colorScheme.tertiary)
            }

            SectionHeader("Intruder attempts (7 days)")
            GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                    if (ui.intrudersPerDay.all { it.value == 0 }) {
                        Text(
                            "No intruder events in the last 7 days. ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BarChart(
                        data = ui.intrudersPerDay.map { it.label to it.value },
                        barColor = MaterialTheme.colorScheme.error,
                    )
                    ui.mostActiveDay?.let {
                        Spacer(Modifier.height(Spacing.sm))
                        Text("Most activity: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionHeader("Most recognized people")
            if (ui.topPeople.isEmpty() || ui.topPeople.all { it.recognitionCount == 0 }) {
                GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Recognition stats will appear here once guarding has seen your enrolled people.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            } else {
                GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        ui.topPeople.filter { it.recognitionCount > 0 }.forEachIndexed { index, p ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconChip(Icons.Filled.Person, size = 38.dp)
                                Spacer(Modifier.width(Spacing.md))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (p.avgConfidence > 0f) "${(p.avgConfidence * 100).toInt()}% avg match" else "-",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("${p.recognitionCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (index < ui.topPeople.lastIndex) com.guardia.app.ui.components.RowDivider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}
