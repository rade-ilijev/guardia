package com.guardia.app.ui.screens.people

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.domain.model.Person
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.HorizontalDivider
import com.guardia.app.ui.components.IconChip
import com.guardia.app.ui.components.PersonAvatar
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Spacing
import com.guardia.app.ui.components.animateEntrance
import androidx.compose.runtime.remember

@Composable
fun PeopleScreen(
    onAddPerson: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onGuestPass: () -> Unit = {},
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val needsReenroll by viewModel.needsReenroll.collectAsStateWithLifecycle()
    val allowed = people.filter { !it.blocked }
    val blockedCount = people.count { it.blocked }

    // Only animate items composed while the screen is arriving; an item scrolled back into view is
    // a fresh composition, and without this the list would re-play its entrance on every scroll up.
    val enteredAt = remember { android.os.SystemClock.uptimeMillis() }
    fun arriving() = android.os.SystemClock.uptimeMillis() - enteredAt < 700L

    GuardiaScaffold(
        title = "People",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPerson,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add person") },
                // Clear the floating navigation bar, not just the system inset.
                modifier = Modifier.padding(bottom = 96.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                top = Spacing.screen,
                end = Spacing.screen,
                bottom = Spacing.bottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (needsReenroll) {
                item {
                    com.guardia.app.ui.components.InfoBanner(
                        "Your existing faces still work. Recognition was upgraded for better accuracy in varied lighting — re-enroll anyone (open them and capture again) when convenient to get the most out of it.",
                        Icons.Filled.Person,
                    )
                }
            }
            item {
                Box(Modifier.animateEntrance(0, arriving())) { BlockedLinkCard(blockedCount, onOpenBlocked) }
            }
            item {
                Box(Modifier.animateEntrance(1, arriving())) { GuestPassCard(onGuestPass) }
            }
            item {
                Column(Modifier.animateEntrance(2, arriving())) {
                    SectionHeader("Allowed people")
                    if (allowed.isEmpty()) {
                        AllowedEmptyState()
                    } else {
                        GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                allowed.forEachIndexed { index, person ->
                                    PersonRow(person, blocked = false, onOpenPerson = onOpenPerson)
                                    if (index < allowed.lastIndex) {
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
}

@Composable
private fun BlockedLinkCard(count: Int, onClick: () -> Unit) {
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(Icons.Filled.Block, tint = MaterialTheme.colorScheme.error, size = 44.dp)
            Spacer(Modifier.size(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("Blocked people", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when (count) {
                        0 -> "Add look-alikes that must never unlock"
                        1 -> "1 person · device locks on match"
                        else -> "$count people · device locks on match"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Lend my phone": entry to the temporary trusted-face flow. */
@Composable
private fun GuestPassCard(onClick: () -> Unit) {
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(Icons.Filled.Person, tint = MaterialTheme.colorScheme.tertiary, size = 44.dp)
            Spacer(Modifier.size(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text("Guest pass", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Lend your phone — trust a face for a few hours, then forget it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One person in the list.
 *
 * Rebuilt on the app's own row language instead of a Material `ListItem` — this was the last list
 * in Guardia still using one, so it sat visibly apart from every other row in the product.
 *
 * The delete button is gone. It was an unconfirmed, single-tap destroy of an enrolled person and
 * every face sample they own, sitting permanently on the row under the user's thumb; the same
 * action already exists behind a confirmation on the person's own screen, which is where an
 * irreversible thing belongs. What replaces it is the chevron every other navigable row in the app
 * has, so the row now says what it does: it opens.
 */
@Composable
internal fun PersonRow(person: Person, blocked: Boolean, onOpenPerson: (String) -> Unit) {
    val c = Guardia.colors
    val guest = person.expiresAt != null
    val tint = when {
        blocked -> c.destructive
        guest -> c.warning
        else -> c.success
    }
    val samples = if (person.sampleCount == 1) "1 face sample" else "${person.sampleCount} face samples"
    val gender = when (person.gender) {
        "MALE" -> "Male"
        "FEMALE" -> "Female"
        else -> null
    }
    // Middle dots, like every other compound caption in the app; the old " - " read as a stray
    // hyphen inside the sentence.
    val caption = when {
        guest -> "Guest · expires ${android.text.format.DateUtils.getRelativeTimeSpanString(person.expiresAt!!)}"
        else -> listOfNotNull(gender, samples).joinToString(" · ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = { onOpenPerson(person.id) },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person.name, person.photoPath, ring = tint, size = 44.dp)
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.foreground,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                // The state is a badge rather than a sentence: "locks on match" buried at the end
                // of a caption is the most important fact about a blocked person.
                if (blocked || guest) {
                    Spacer(Modifier.size(Spacing.sm))
                    com.guardia.app.ui.components.ShBadge(
                        text = if (blocked) "Blocked" else "Guest",
                        variant = if (blocked) {
                            com.guardia.app.ui.components.BadgeVariant.Destructive
                        } else {
                            com.guardia.app.ui.components.BadgeVariant.Warning
                        },
                    )
                }
            }
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = c.mutedForeground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(Spacing.sm))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.mutedForeground,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AllowedEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("No one enrolled yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add yourself and trusted people so Guardia knows who is allowed to use this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
