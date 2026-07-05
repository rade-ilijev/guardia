package com.guardia.app.ui.screens.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.InfoBanner
import com.guardia.app.ui.components.BannerTone
import com.guardia.app.ui.theme.Spacing

@Composable
fun BlockedPeopleScreen(
    onBack: () -> Unit,
    onAddBlocked: () -> Unit,
    onOpenPerson: (String) -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val blocked = people.filter { it.blocked }

    GuardiaScaffold(
        title = "Blocked people",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBlocked,
                icon = { Icon(Icons.Filled.Block, contentDescription = null) },
                text = { Text("Add blocked") },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(bottom = 84.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.screen, end = Spacing.screen, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                InfoBanner(
                    "Guardia locks the device whenever it recognizes anyone here - useful for look-alikes (e.g. a sibling) who could otherwise pass.",
                    Icons.Filled.Block,
                    tone = BannerTone.Warning,
                )
            }
            if (blocked.isEmpty()) {
                item { EmptyState() }
            } else {
                item {
                    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            blocked.forEachIndexed { index, person ->
                                PersonRow(person, blocked = true, onOpenPerson = onOpenPerson) { viewModel.remove(person.id) }
                                if (index < blocked.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = 72.dp),
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

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text("No blocked people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap \"Add blocked\" to add photos of someone who must never unlock this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
