package com.guardia.app.ui.screens.intruders

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.domain.model.IntruderCapture
import com.guardia.app.ui.components.EmptyState
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.theme.Spacing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntrudersScreen(
    onBack: () -> Unit,
    viewModel: IntrudersViewModel = hiltViewModel(),
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val people by viewModel.peopleList.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<IntruderCapture?>(null) }
    var pickFor by remember { mutableStateOf<IntruderCapture?>(null) }
    var blockFor by remember { mutableStateOf<IntruderCapture?>(null) }
    var blockName by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    GuardiaScaffold(
        title = "Intruders",
        onBack = onBack,
        actions = {
            if (captures.isNotEmpty()) {
                IconButton(onClick = { viewModel.clear() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all")
                }
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
                    start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 100.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                itemsIndexed(captures, key = { _, c -> c.id }) { index, capture ->
                    CaptureCell(capture, index, viewModel) { preview = capture }
                }
            }
        }
    }

    preview?.let { capture ->
        Dialog(onDismissRequest = { preview = null }) {
            PreviewSheet(
                capture = capture,
                viewModel = viewModel,
                onAddFace = {
                    if (people.isEmpty()) {
                        Toast.makeText(context, "Add a person first", Toast.LENGTH_SHORT).show()
                    } else {
                        pickFor = capture; preview = null
                    }
                },
                onBlock = { blockName = ""; blockFor = capture; preview = null },
                onDelete = { viewModel.delete(capture); preview = null },
            )
        }
    }

    pickFor?.let { capture ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text("Add face to") },
            text = {
                Column {
                    people.forEach { person ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.assignToPerson(capture, person.id, person.name) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                pickFor = null
                            },
                            headlineContent = { Text(if (person.blocked) "${person.name} (blocked)" else person.name) },
                            supportingContent = { Text("${person.sampleCount} sample(s)") },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickFor = null }) { Text("Cancel") } },
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
                    androidx.compose.material3.OutlinedTextField(
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

@Composable
private fun CaptureCell(
    capture: IntruderCapture,
    index: Int,
    viewModel: IntrudersViewModel,
    onClick: () -> Unit,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, capture.id) {
        value = viewModel.loadBitmap(capture.photoPath)
    }
    // Staggered entrance: fade + rise + slight scale as each tile first appears.
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(capture.id) {
        delay((index.coerceAtMost(12) * 40).toLong())
        shown = true
    }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(360), label = "appear")

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
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            // Bottom gradient scrim so the caption reads on any photo.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(82.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.LockClock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        capture.source,
                        style = MaterialTheme.typography.labelMedium,
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
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewSheet(
    capture: IntruderCapture,
    viewModel: IntrudersViewModel,
    onAddFace: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, capture.id) {
        value = viewModel.loadBitmap(capture.photoPath)
    }
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.sm)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = Spacing.sm, vertical = 4.dp),
                ) {
                    Text(capture.source, style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Button(onClick = onAddFace, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Add this face to a person")
            }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onBlock, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Block, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Add as blocked person")
            }
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Delete photo")
            }
        }
    }
}
