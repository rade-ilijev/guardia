package com.guardia.app.ui.screens.people

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.guardia.app.ui.components.Button
import com.guardia.app.ui.components.FilterChip
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.OutlinedButton
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Spacing

private val GUEST_DURATIONS = listOf(
    30L * 60_000 to "30 min",
    60L * 60_000 to "1 hour",
    4L * 60 * 60_000 to "4 hours",
    8L * 60 * 60_000 to "8 hours",
)

/**
 * "Lend my phone": a quick front-only face scan that enrolls a temporary trusted person. The
 * pass expires on its own — the guard treats them as unknown again and the profile is purged.
 */
@Composable
fun GuestPassScreen(
    onDone: () -> Unit,
    viewModel: EnrollmentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var hasCamera by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    var durationIndex by rememberSaveable { mutableStateOf(1) }

    LaunchedEffect(hasCamera) {
        if (hasCamera && ui.phase == EnrollPhase.READY) {
            viewModel.start(checkDuplicates = false, quick = true)
        }
    }

    GuardiaScaffold(
        title = "Guest pass",
        onBack = onDone,
        bottomBar = {
            if (hasCamera) {
                Surface(color = Color.Transparent) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (ui.phase == EnrollPhase.VERIFIED) {
                            Button(
                                onClick = { viewModel.saveGuest(GUEST_DURATIONS[durationIndex].first, onDone) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) { Text("Start guest pass · ${GUEST_DURATIONS[durationIndex].second}") }
                            OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                                Text("Recapture")
                            }
                        } else {
                            Text(
                                "A quick scan of your guest's face — they can use the phone until the pass expires.",
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
                    Text("Camera permission is needed to scan your guest's face.", textAlign = TextAlign.Center)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            ) {
                GUEST_DURATIONS.forEachIndexed { index, (_, label) ->
                    FilterChip(
                        selected = durationIndex == index,
                        onClick = { durationIndex = index },
                        label = { Text(label) },
                    )
                }
            }

            val ringColor = when (ui.phase) {
                EnrollPhase.VERIFIED, EnrollPhase.SAVED -> Guardia.colors.success
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
            }

            GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        ui.message,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "When the pass expires, this face is forgotten and Guardia treats them as unknown again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
