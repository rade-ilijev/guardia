package com.guardia.app.ui.screens.people

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.ui.components.AnalysisCamera
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.theme.Spacing

@Composable
fun AddPersonScreen(
    onDone: () -> Unit,
    personId: String? = null,
    viewModel: EnrollmentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat_checkCamera(context)
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    val addingSamples = personId != null

    GuardiaScaffold(
        title = if (addingSamples) "Add more samples" else "Enroll a person",
        onBack = onDone,
        bottomBar = {
            if (hasCamera) {
                Surface(color = Color.Transparent) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        when (ui.phase) {
                            EnrollPhase.READY -> Button(
                                onClick = viewModel::start,
                                enabled = addingSamples || name.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text("Start capture") }

                            EnrollPhase.VERIFIED -> {
                                Button(
                                    onClick = { viewModel.save(name, gender, personId, onDone) },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                ) { Text(if (addingSamples) "Save samples" else "Save person") }
                                OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                                    Text("Recapture")
                                }
                            }

                            else -> Text(
                                "Hold still — keep your face inside the circle.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (!hasCamera) {
            Box(Modifier.fillMaxSize().padding(padding).padding(Spacing.screen), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is needed to enroll a face.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Spacing.md))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant camera")
                    }
                }
            }
            return@GuardiaScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!addingSamples) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = ui.phase == EnrollPhase.READY,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Gender", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.sm))
                    listOf("MALE" to "Male", "FEMALE" to "Female").forEach { (key, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = gender == key,
                            onClick = { gender = if (gender == key) null else key },
                            label = { Text(label) },
                            enabled = ui.phase == EnrollPhase.READY,
                        )
                    }
                }
            }

            val ringColor = when (ui.phase) {
                EnrollPhase.VERIFIED, EnrollPhase.SAVED -> MaterialTheme.colorScheme.primary
                else -> Color.White.copy(alpha = 0.6f)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                AnalysisCamera(onFrame = viewModel::onFrame, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(0.8f)
                        .clip(CircleShape)
                        .border(3.dp, ringColor, CircleShape),
                )
                if (ui.phase == EnrollPhase.CAPTURING) {
                    ui.requiredPose?.let { PoseArrow(it) }
                }
                if (ui.phase == EnrollPhase.VERIFYING) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                if (ui.phase == EnrollPhase.VERIFIED) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp),
                    )
                }
            }

            GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        ui.message,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )

                    if (ui.phase == EnrollPhase.CAPTURING || ui.phase == EnrollPhase.VERIFYING) {
                        LinearProgressIndicator(
                            progress = { ui.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            com.guardia.app.ui.screens.people.ENROLL_POSES.forEach { step ->
                                PoseChip(
                                    label = step.label,
                                    done = step.pose in ui.completedPoses,
                                    active = ui.phase == EnrollPhase.CAPTURING && ui.requiredPose == step.pose,
                                )
                            }
                        }
                    }

                    Text(
                        "Turn slowly through each prompt so Guardia learns your face from every angle. Good, even lighting helps a lot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun PoseArrow(pose: com.guardia.app.core.ml.FaceQualityAnalyzer.HeadPose) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "poseArrow")
    val offset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "poseArrowOffset",
    )
    val (icon, align, dx, dy) = when (pose) {
        com.guardia.app.core.ml.FaceQualityAnalyzer.HeadPose.RIGHT ->
            Quad(Icons.AutoMirrored.Filled.ArrowForward, Alignment.CenterEnd, offset, 0f)
        com.guardia.app.core.ml.FaceQualityAnalyzer.HeadPose.LEFT ->
            Quad(Icons.AutoMirrored.Filled.ArrowBack, Alignment.CenterStart, -offset, 0f)
        com.guardia.app.core.ml.FaceQualityAnalyzer.HeadPose.UP ->
            Quad(Icons.Filled.KeyboardArrowUp, Alignment.TopCenter, 0f, -offset)
        com.guardia.app.core.ml.FaceQualityAnalyzer.HeadPose.DOWN ->
            Quad(Icons.Filled.KeyboardArrowDown, Alignment.BottomCenter, 0f, offset)
        com.guardia.app.core.ml.FaceQualityAnalyzer.HeadPose.CENTER ->
            Quad(Icons.Filled.CheckCircle, Alignment.Center, 0f, 0f)
    }
    Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = align) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .offset(x = dx.dp, y = dy.dp)
                .size(48.dp),
        )
    }
}

private data class Quad(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val align: Alignment,
    val dx: Float,
    val dy: Float,
)

@Composable
private fun PoseChip(label: String, done: Boolean, active: Boolean) {
    val color = when {
        done -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (done) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(
                Modifier
                    .size(20.dp)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .border(2.dp, color, CircleShape),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun ContextCompat_checkCamera(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
