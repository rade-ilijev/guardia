package com.guardia.app.ui.screens.people

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GalleryImportScreen(
    onBack: () -> Unit,
    viewModel: GalleryImportViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val name by viewModel.personName.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris -> if (uris.isNotEmpty()) viewModel.scan(uris) }

    val launchPicker: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    GuardiaScaffold(title = "Import from gallery", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (ui.phase) {
                ImportPhase.IDLE -> IdleContent(name, launchPicker)
                ImportPhase.SCANNING -> ScanningContent(ui)
                ImportPhase.REVIEW -> ReviewContent(ui, name, viewModel)
                ImportPhase.DONE -> DoneContent(ui, launchPicker, onBack)
            }
        }
    }
}

@Composable
private fun IdleContent(name: String, onChoose: () -> Unit) {
    Spacer(Modifier.height(Spacing.sm))
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text("Teach Guardia from your photos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Pick photos and Guardia finds every face in them. Swipe right if it's $name, left if " +
                    "it's not (helps reject look-alikes), or down to blacklist that face. Everything stays on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
        Spacer(Modifier.size(Spacing.sm))
        Text("Choose photos")
    }
}

@Composable
private fun ScanningContent(ui: GalleryImportUi) {
    Spacer(Modifier.height(Spacing.xl))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(Spacing.lg))
        Text("Scanning photos for faces…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text("${ui.scanned}/${ui.total} photos · ${ui.candidates.size} faces found",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.md))
        LinearProgressIndicator(
            progress = { if (ui.total == 0) 0f else ui.scanned.toFloat() / ui.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.ReviewContent(ui: GalleryImportUi, name: String, viewModel: GalleryImportViewModel) {
    val candidate = ui.current ?: return
    Spacer(Modifier.height(Spacing.sm))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${ui.remaining} left", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Match: ${candidate.matchPercent}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = matchColor(candidate.matchPercent))
    }

    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        SwipeCard(
            key = candidate.id,
            photoPath = candidate.photoPath,
            matchPercent = candidate.matchPercent,
            onConfirm = viewModel::confirm,
            onDecline = viewModel::decline,
            onBlacklist = viewModel::blacklist,
        )
    }

    Text(
        "Swipe right if this is $name · left if it isn't · down to blacklist",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = viewModel::decline, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp)); Text("Not them")
        }
        FilledTonalButton(onClick = viewModel::blacklist, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp)); Text("Block")
        }
        Button(onClick = viewModel::confirm, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp)); Text("It's them")
        }
    }
    Spacer(Modifier.height(Spacing.sm))
}

@Composable
private fun SwipeCard(
    key: String,
    photoPath: String,
    matchPercent: Int,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onBlacklist: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = screenWidthPx * 0.28f
    val offsetX = remember(key) { Animatable(0f) }
    val offsetY = remember(key) { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(0.74f)
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = (offsetX.value / 60f).coerceIn(-12f, 12f)
            }
            .pointerInput(key) {
                detectDragGestures(
                    onDragEnd = {
                        val x = offsetX.value
                        val y = offsetY.value
                        when {
                            x > threshold -> flingOff(scope, offsetX, screenWidthPx * 1.5f, onConfirm)
                            x < -threshold -> flingOff(scope, offsetX, -screenWidthPx * 1.5f, onDecline)
                            y > threshold -> flingOff(scope, offsetY, screenWidthPx * 1.6f, onBlacklist)
                            else -> {
                                scope.launch { offsetX.animateTo(0f, tween(220)) }
                                scope.launch { offsetY.animateTo(0f, tween(220)) }
                            }
                        }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + drag.x) }
                    scope.launch { offsetY.snapTo(offsetY.value + drag.y) }
                }
            },
    ) {
        GuardiaCard(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .clip(CircleShape).background(Color(0xCC0E1413)).padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("${matchPercent}% match", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun flingOff(
    scope: kotlinx.coroutines.CoroutineScope,
    axis: Animatable<Float, AnimationVector1D>,
    target: Float,
    action: () -> Unit,
) {
    scope.launch {
        axis.animateTo(target, tween(220))
        action()
    }
}

@Composable
private fun DoneContent(ui: GalleryImportUi, onChoose: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.height(Spacing.lg))
    SectionHeader("All done")
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryRow("Confirmed as this person", ui.confirmed, MaterialTheme.colorScheme.primary)
            SummaryRow("Marked as someone else", ui.declined, MaterialTheme.colorScheme.onSurfaceVariant)
            SummaryRow("Blacklisted", ui.blacklisted, MaterialTheme.colorScheme.error)
        }
    }
    if (ui.candidates.isEmpty()) {
        Text("No faces were found in those photos.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null); Spacer(Modifier.size(Spacing.sm)); Text("Choose more photos")
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done") }
}

@Composable
private fun SummaryRow(label: String, count: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun matchColor(percent: Int): Color = when {
    percent >= 70 -> MaterialTheme.colorScheme.primary
    percent >= 45 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
