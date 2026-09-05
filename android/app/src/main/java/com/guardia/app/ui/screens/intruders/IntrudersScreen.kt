package com.guardia.app.ui.screens.intruders

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.domain.model.IntruderCapture
import com.guardia.app.ui.components.EmptyState
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.ButtonVariant
import com.guardia.app.ui.components.OutlinedTextField
import com.guardia.app.ui.components.ShButton
import com.guardia.app.ui.components.TextButton
import com.guardia.app.ui.theme.DataDisplay
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.OverlineStyle
import com.guardia.app.ui.theme.Spacing
import com.guardia.app.ui.theme.StatusReadout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import com.guardia.app.ui.components.GlassIconButton
import androidx.compose.ui.platform.LocalDensity

/**
 * Consecutive shots of the same incident (e.g. the 3-frame burst on a wrong PIN), newest first.
 * Grouped by source + proximity in time so one break-in attempt reads as one tile, not three.
 *
 * Marked `@Immutable` for Compose: it holds a `List`, and Compose treats every `List` as unstable
 * because the interface allows a mutable implementation. Without the annotation, any composable
 * reading this state is re-run on *every* recomposition of its parent, even when the state itself
 * has not changed. The contents genuinely are never mutated after construction, so the promise is
 * safe to make — and it is what lets Compose skip the subtree.
 */
@Immutable
private data class Incident(val shots: List<IntruderCapture>) {
    val primary: IntruderCapture get() = shots.first()
}

/** [captures] must be sorted newest-first (the DAO guarantees it). */
private fun groupIncidents(captures: List<IntruderCapture>): List<Incident> {
    if (captures.isEmpty()) return emptyList()
    val incidents = mutableListOf<MutableList<IntruderCapture>>()
    for (c in captures) {
        val current = incidents.lastOrNull()
        val previous = current?.last()
        if (previous != null && previous.source == c.source &&
            previous.timestamp - c.timestamp <= INCIDENT_GAP_MS
        ) {
            current.add(c)
        } else {
            incidents.add(mutableListOf(c))
        }
    }
    return incidents.map { Incident(it) }
}

/** Max gap between shots of one incident — bursts are ~700ms apart; PIN retries are throttled to 15s. */
private const val INCIDENT_GAP_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntrudersScreen(
    onBack: () -> Unit,
    viewModel: IntrudersViewModel = hiltViewModel(),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val incidents = remember(captures) { groupIncidents(captures) }
    val people by viewModel.peopleList.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<IntruderCapture?>(null) }
    var pickFor by remember { mutableStateOf<IntruderCapture?>(null) }
    var blockFor by remember { mutableStateOf<IntruderCapture?>(null) }
    var blockName by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    GuardiaScaffold(
        title = "Intruders",
        onBack = onBack,
        actions = {
            if (captures.isNotEmpty()) {
                GlassIconButton(
                    Icons.Filled.DeleteSweep,
                    onClick = { confirmClear = true },
                    contentDescription = "Clear all",
                    tint = Guardia.colors.destructive,
                )
            }
        },
    ) { padding ->
        if (captures.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Shield,
                title = "No intruder photos",
                subtitle = "Selfies from wrong unlocks, failed face checks, and tamper attempts will show up here.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.sm,
                    bottom = Spacing.bottomBarClearance,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(span = { GridItemSpan(2) }) { EvidenceHeader(incidents) }
                itemsIndexed(incidents, key = { _, inc -> inc.primary.id }) { index, incident ->
                    CaptureCell(incident, index, viewModel) { preview = incident.primary }
                }
            }
        }
    }

    preview?.let { capture ->
        // Resolve the incident this shot belongs to from the *current* captures, so deletes and
        // repository updates flow through; if the shot vanished, fall out of the viewer.
        val incident = incidents.firstOrNull { inc -> inc.shots.any { it.id == capture.id } }
        if (incident == null) {
            preview = null
            return@let
        }
        Dialog(onDismissRequest = { preview = null }) {
            EvidenceViewer(
                incident = incident,
                selected = capture,
                viewModel = viewModel,
                onSelect = { preview = it },
                onClose = { preview = null },
                onAddFace = {
                    if (people.isEmpty()) {
                        Toast.makeText(context, "Add a person first", Toast.LENGTH_SHORT).show()
                    } else {
                        pickFor = capture; preview = null
                    }
                },
                onBlock = { blockName = ""; blockFor = capture; preview = null },
                onShare = {
                    viewModel.exportCapture(capture) { uri, caption ->
                        if (uri == null) {
                            Toast.makeText(context, "Couldn't prepare this photo to share", Toast.LENGTH_SHORT).show()
                            return@exportCapture
                        }
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            putExtra(android.content.Intent.EXTRA_TEXT, caption)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(android.content.Intent.createChooser(send, "Share evidence"))
                        }.onFailure { Toast.makeText(context, "No app to share to", Toast.LENGTH_SHORT).show() }
                    }
                    preview = null
                },
                onDelete = {
                    // Stay in the viewer on the incident's next shot; close when none remain.
                    val next = incident.shots.firstOrNull { it.id != capture.id }
                    viewModel.delete(capture)
                    preview = next
                },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            title = { Text("Delete all evidence?") },
            text = { Text("All ${captures.size} intruder photos will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clear(); confirmClear = false }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    pickFor?.let { capture ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            containerColor = Guardia.colors.popover,
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Guardia.colors.brand) },
            title = { Text("Whose face is this?", style = MaterialTheme.typography.titleLarge) },
            text = {
                // Scrollable: a dialog's text slot does not scroll on its own, and someone with a
                // household's worth of enrolled faces would otherwise find the list clipped with
                // no way to reach the bottom of it.
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        "The photo becomes another training sample for whoever you pick.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Guardia.colors.mutedForeground,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    people.forEach { person ->
                        PersonPickRow(
                            name = person.name,
                            detail = if (person.blocked) {
                                "Blocked · ${person.sampleCount} samples"
                            } else {
                                "${person.sampleCount} samples"
                            },
                            blocked = person.blocked,
                            onClick = {
                                viewModel.assignToPerson(capture, person.id, person.name) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                pickFor = null
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                ShButton("Cancel", onClick = { pickFor = null }, variant = ButtonVariant.Ghost)
            },
        )
    }

    blockFor?.let { capture ->
        AlertDialog(
            onDismissRequest = { blockFor = null },
            icon = { Icon(Icons.Filled.Block, contentDescription = null) },
            title = { Text("Block this person") },
            text = {
                Column {
                    Text("Guardia will lock the device whenever it recognizes this face.")
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = blockName,
                        onValueChange = { blockName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = blockName.isNotBlank(),
                    onClick = {
                        viewModel.createBlockedFromCapture(capture, blockName) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        blockFor = null
                    },
                ) { Text("Block") }
            },
            dismissButton = { TextButton(onClick = { blockFor = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Header above the evidence grid: how much is on record, and how fresh it is.
 *
 * It used to set "EVIDENCE LOG" in alert red above a display-sized number, which — over a grid of
 * photographs that were themselves ringed in red — meant the loudest thing on a screen full of
 * loud things was a label. The count is the finding; red is spent on the one part that is actually
 * news, which is whether any of this happened today.
 */
@Composable
private fun EvidenceHeader(incidents: List<Incident>) {
    val c = Guardia.colors
    val latest = incidents.maxOfOrNull { it.primary.timestamp }
    val fresh = latest != null && System.currentTimeMillis() - latest < 24L * 60 * 60 * 1000
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text("ON RECORD", style = OverlineStyle, color = c.mutedForeground)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(incidents.size.toString(), style = DataDisplay, color = c.foreground)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    if (incidents.size == 1) "incident" else "incidents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        if (latest != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("LATEST", style = OverlineStyle, color = c.mutedForeground)
                Spacer(Modifier.height(2.dp))
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        latest, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    style = StatusReadout,
                    color = if (fresh) c.destructive else c.foreground,
                )
            }
        }
    }
}

@Composable
private fun CaptureCell(
    incident: Incident,
    index: Int,
    viewModel: IntrudersViewModel,
    onClick: () -> Unit,
) {
    val capture = incident.primary
    // A grid tile is about half the screen wide; ask for that, not the full frame.
    val tilePx = with(LocalDensity.current) { 220.dp.roundToPx() }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, capture.id) {
        value = viewModel.loadThumbnail(capture.photoPath, tilePx)
    }
    // Staggered entrance: fade + rise + slight scale as each tile first appears.
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(capture.id) {
        delay((index.coerceAtMost(12) * 40).toLong())
        shown = true
    }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(360), label = "appear")

    // No coral frame. Every tile on this screen is evidence, so ringing every one of them in alert
    // red said nothing and turned the grid into a wall of warnings — the photograph is the content,
    // and it should be what the eye lands on. The recording chip still marks each shot as capture.
    GuardiaCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .graphicsLayer {
                alpha = appear
                scaleX = 0.92f + 0.08f * appear
                scaleY = 0.92f + 0.08f * appear
                translationY = (1f - appear) * 28f
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(Guardia.colors.muted),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = Guardia.colors.mutedForeground.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            // Recording chip: pulsing-dot style timestamp, top-right like camera footage.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Guardia.colors.destructive),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    timeLabel(capture.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            // Burst badge: this incident holds multiple shots.
            if (incident.shots.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.sm)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.BurstMode,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "×${incident.shots.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
            // Bottom gradient scrim so the caption reads on any photo.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Text(
                    capture.source.uppercase(),
                    style = OverlineStyle,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        capture.timestamp,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The evidence viewer: full photo in a coral case-file frame, a shot strip when the incident
 * holds a burst, a mono metadata strip (time, date, age), and a 2x2 action grid.
 */
@Composable
private fun EvidenceViewer(
    incident: Incident,
    selected: IntruderCapture,
    viewModel: IntrudersViewModel,
    onSelect: (IntruderCapture) -> Unit,
    onClose: () -> Unit,
    onAddFace: () -> Unit,
    onBlock: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val capture = selected
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, capture.id) {
        value = viewModel.loadBitmap(capture.photoPath)
    }
    val shotIndex = incident.shots.indexOfFirst { it.id == capture.id }.coerceAtLeast(0)
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(Spacing.md)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (incident.shots.size > 1) "EVIDENCE · SHOT ${shotIndex + 1}/${incident.shots.size}"
                        else "EVIDENCE",
                        style = OverlineStyle,
                        color = Guardia.colors.mutedForeground,
                    )
                    Text(
                        capture.source,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Guardia.colors.mutedForeground)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black)
                    .border(1.dp, Guardia.colors.border, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            if (incident.shots.size > 1) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    incident.shots.forEach { shot ->
                        ShotThumb(
                            shot = shot,
                            selected = shot.id == capture.id,
                            viewModel = viewModel,
                            onClick = { onSelect(shot) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetaCell("TIME", timeLabel(capture.timestamp), Modifier.weight(1f))
                MetaCell("DATE", dateLabel(capture.timestamp), Modifier.weight(1f))
                MetaCell(
                    "AGE",
                    DateUtils.getRelativeTimeSpanString(
                        capture.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            // Four buttons from the app's own set rather than a 2x2 of tinted tiles. The tiles gave
            // "Identify" and "Share" a green wash and their own 18dp corner, which belonged to no
            // other surface in the app; and colouring half the actions green while the other half
            // went red implied a safe/dangerous split that "Share evidence" does not fit.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ShButton(
                    text = "Identify",
                    onClick = onAddFace,
                    variant = ButtonVariant.Outline,
                    leadingIcon = Icons.Filled.PersonSearch,
                    modifier = Modifier.weight(1f),
                )
                ShButton(
                    text = "Share",
                    onClick = onShare,
                    variant = ButtonVariant.Outline,
                    leadingIcon = Icons.Filled.Share,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ShButton(
                    text = "Block",
                    onClick = onBlock,
                    variant = ButtonVariant.Destructive,
                    leadingIcon = Icons.Filled.Block,
                    modifier = Modifier.weight(1f),
                )
                ShButton(
                    text = "Delete",
                    onClick = onDelete,
                    variant = ButtonVariant.Destructive,
                    leadingIcon = Icons.Filled.DeleteOutline,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Small selectable thumbnail in the viewer's shot strip; the active shot wears the coral ring. */
@Composable
private fun ShotThumb(
    shot: IntruderCapture,
    selected: Boolean,
    viewModel: IntrudersViewModel,
    onClick: () -> Unit,
) {
    val stripPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, shot.id) {
        value = viewModel.loadThumbnail(shot.photoPath, stripPx)
    }
    val c = Guardia.colors
    val shape = RoundedCornerShape(com.guardia.app.ui.theme.Radius.xl)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(shape)
            .background(c.muted)
            // The ring marks which shot you are looking at, which is a selection and not a danger —
            // it wears the brand colour, and alert red stays reserved for meaning alert.
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) c.brand else c.border,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** One mono metadata readout in the strip under the photo. */
@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = OverlineStyle, color = Guardia.colors.mutedForeground)
        Spacer(Modifier.height(2.dp))
        Text(value, style = StatusReadout, color = Guardia.colors.foreground, maxLines = 1)
    }
}

/** One tappable person in the "whose face is this?" dialog. */
@Composable
private fun PersonPickRow(name: String, detail: String, blocked: Boolean, onClick: () -> Unit) {
    val c = Guardia.colors
    val shape = RoundedCornerShape(com.guardia.app.ui.theme.Radius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (blocked) Icons.Filled.Block else Icons.Filled.PersonSearch,
            contentDescription = null,
            tint = if (blocked) c.destructive else c.mutedForeground,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = c.foreground, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground, maxLines = 1)
        }
    }
}

private fun timeLabel(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun dateLabel(timestamp: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
