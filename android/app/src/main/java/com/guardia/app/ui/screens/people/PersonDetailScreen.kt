package com.guardia.app.ui.screens.people

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.guardia.app.domain.model.Person
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.IconChip
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.components.StatTile
import com.guardia.app.ui.theme.Spacing
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onAddFaces: (String) -> Unit,
    onImportGallery: (String) -> Unit = {},
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val person by viewModel.person.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var sampleToDelete by remember { mutableStateOf<com.guardia.app.domain.model.FaceSample?>(null) }

    GuardiaScaffold(
        title = person?.name ?: "Person",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showRename = true }) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
            IconButton(onClick = { showDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.sm))
            ProfileHeader(person)

            SectionHeader("Statistics")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    Icons.Filled.PhotoLibrary,
                    (person?.sampleCount ?: 0).toString(),
                    "Face samples",
                    Modifier.weight(1f),
                )
                StatTile(
                    Icons.Filled.Verified,
                    (person?.recognitionCount ?: 0).toString(),
                    "Recognitions",
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                val conf = person?.avgConfidence ?: 0f
                StatTile(
                    Icons.Filled.Insights,
                    if (conf > 0f) "${(conf * 100).roundToInt()}%" else "-",
                    "Avg match",
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                StatTile(
                    Icons.Filled.Schedule,
                    seenShort(person?.lastSeenAt),
                    "Last seen",
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }

            val conf = person?.avgConfidence ?: 0f
            val recs = person?.recognitionCount ?: 0
            if (conf in 0.001f..0.55f && recs >= 3) {
                com.guardia.app.ui.components.InfoBanner(
                    "Recognition confidence for this person is low (${(conf * 100).roundToInt()}%). Add more face samples in varied lighting and angles to improve accuracy.",
                    Icons.Filled.Insights,
                    tone = com.guardia.app.ui.components.BannerTone.Warning,
                )
            }

            val isBlocked = person?.blocked == true
            SectionHeader("Authorization")
            if (isBlocked) {
                com.guardia.app.ui.components.InfoBanner(
                    "Blocked person. Guardia locks the device whenever it recognizes this face.",
                    Icons.Filled.Block,
                    tone = com.guardia.app.ui.components.BannerTone.Warning,
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { viewModel.setBlocked(false) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Move to allowed people") }
            } else {
                com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                    com.guardia.app.ui.components.SwitchRow(
                        title = "Authorized to use this device",
                        subtitle = if (person?.enabled != false)
                            "Guarding recognizes this person and stays unlocked."
                        else "Temporarily ignored - treated as unauthorized.",
                        checked = person?.enabled != false,
                        onCheckedChange = { viewModel.setEnabled(it) },
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { viewModel.setBlocked(true) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Spacing.sm))
                    Text("Move to blocked list")
                }
            }

            SectionHeader("Gender")
            com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("MALE" to "Male", "FEMALE" to "Female").forEach { (key, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = person?.gender == key,
                            onClick = { viewModel.setGender(if (person?.gender == key) null else key) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            SectionHeader("Recognition quality")
            QualityCard(sampleCount = person?.sampleCount ?: 0)

            val photoSamples = samples.filter { it.photoPath != null }
            if (photoSamples.isNotEmpty()) {
                SectionHeader("Training photos (${photoSamples.size})")
                TrainingPhotos(photoSamples) { sampleToDelete = it }
            }

            Spacer(Modifier.height(Spacing.xs))
            if (!isBlocked) {
                Button(
                    onClick = { onAddFaces(viewModel.personId) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.size(Spacing.sm))
                    Text("Add more faces")
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { onImportGallery(viewModel.personId) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Spacing.sm))
                    Text("Import from gallery")
                }
            }
            OutlinedButton(
                onClick = { showRename = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text("Rename person")
            }
            TextButton(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remove person", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(100.dp))
        }
    }

    sampleToDelete?.let { sample ->
        AlertDialog(
            onDismissRequest = { sampleToDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove this training photo?") },
            text = { Text("Guardia will no longer use this face sample for recognition.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSample(sample.id); sampleToDelete = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { sampleToDelete = null }) { Text("Cancel") } },
        )
    }

    if (showRename) {
        RenameDialog(
            currentName = person?.name ?: "",
            onDismiss = { showRename = false },
            onConfirm = { viewModel.rename(it); showRename = false },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove ${person?.name ?: "person"}?") },
            text = { Text("This permanently deletes their ${person?.sampleCount ?: 0} face sample(s) and recognition history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; viewModel.delete(onBack) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileHeader(person: Person?) {
    val blocked = person?.blocked == true
    val accent = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                val photo = person?.photoPath
                if (photo != null) {
                    AsyncImage(
                        model = File(photo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(if (blocked) Icons.Filled.Block else Icons.Filled.Person, contentDescription = null, tint = accent, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.size(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    person?.name ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (blocked) Icons.Filled.Block else Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (blocked) "Blocked" else "Authorized", style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
                }
                person?.createdAt?.let {
                    Text(
                        "Enrolled ${DateUtils.getRelativeTimeSpanString(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainingPhotos(
    samples: List<com.guardia.app.domain.model.FaceSample>,
    onDelete: (com.guardia.app.domain.model.FaceSample) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(samples, key = { it.id }) { sample ->
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = File(sample.photoPath ?: ""),
                    contentDescription = "Training photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { onDelete(sample) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityCard(sampleCount: Int) {
    val (label, frac, color) = when {
        sampleCount >= 5 -> Triple("Excellent", 1f, MaterialTheme.colorScheme.primary)
        sampleCount >= 3 -> Triple("Good", 0.7f, MaterialTheme.colorScheme.primary)
        sampleCount >= 1 -> Triple("Basic", 0.35f, MaterialTheme.colorScheme.tertiary)
        else -> Triple("No samples", 0.05f, MaterialTheme.colorScheme.error)
    }
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Filled.Insights, tint = color)
                Spacer(Modifier.size(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enrollment strength", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$sampleCount sample(s) enrolled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color)
            }
            Spacer(Modifier.height(Spacing.md))
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                if (sampleCount >= 5) "Great coverage. Recognition should be reliable across lighting and angles."
                else "Add more samples across angles and lighting to improve recognition accuracy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newName by remember { mutableStateOf(currentName) }
    val trimmed = newName.trim()
    val valid = trimmed.isNotEmpty() && trimmed != currentName && trimmed.length <= 30
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Rename person") },
        text = {
            Column {
                Text(
                    "Choose a name you'll recognize in the activity log and alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 30) newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = newName.isNotEmpty() && trimmed.isEmpty(),
                    supportingText = { Text("${newName.length}/30") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun seenShort(ts: Long?): String {
    if (ts == null) return "Never"
    val diff = System.currentTimeMillis() - ts
    val min = diff / 60000
    return when {
        min < 1 -> "Now"
        min < 60 -> "${min}m"
        min < 1440 -> "${min / 60}h"
        else -> "${min / 1440}d"
    }
}
