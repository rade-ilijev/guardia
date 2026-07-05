package com.guardia.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaLogo
import com.guardia.app.ui.components.IconChip
import com.guardia.app.ui.components.PremiumBadge
import com.guardia.app.ui.components.StatusOrb
import com.guardia.app.ui.screens.people.AddPersonScreen
import com.guardia.app.ui.theme.Spacing

private enum class Step { WELCOME, HOW, PINS, PERMISSIONS, ENROLL, LOCKING, APP_DETECTION, LOCATIONS, DONE }

private val steps = Step.entries

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var index by remember { mutableIntStateOf(0) }
    var real by remember { mutableStateOf("") }
    var decoy by remember { mutableStateOf("") }
    var panic by remember { mutableStateOf("") }
    val hasFace by viewModel.hasEnrolledFace.collectAsStateWithLifecycle()
    val premium by viewModel.premium.collectAsStateWithLifecycle()

    val step = steps[index]
    var legalDoc by remember { mutableStateOf<Int?>(null) }
    val advance: () -> Unit = {
        if (index < steps.lastIndex) index++ else viewModel.finish(onComplete)
    }

    legalDoc?.let { rawRes ->
        com.guardia.app.ui.components.LegalDocDialog(
            title = if (rawRes == com.guardia.app.R.raw.privacy_policy) "Privacy Policy" else "Terms of Use",
            rawRes = rawRes,
            onDismiss = { legalDoc = null },
        )
    }

    // The enrollment step takes over the whole screen (it brings its own camera UI and chrome).
    if (step == Step.ENROLL) {
        AddPersonScreen(onDone = advance)
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
        Spacer(Modifier.height(Spacing.md))
        LinearProgressIndicator(
            progress = { (index + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Step ${index + 1} of ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (step.optional) {
                TextButton(onClick = advance) { Text("Skip") }
            }
        }

        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(250)) { dir * it / 3 } + fadeIn()) togetherWith fadeOut(tween(150))
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { i ->
            when (steps[i]) {
                Step.WELCOME -> WelcomeStep(onShowLegal = { legalDoc = it })
                Step.HOW -> HowItWorksStep()
                Step.PINS -> PinStep(real, { real = it }, decoy, { decoy = it }, panic, { panic = it })
                Step.PERMISSIONS -> PermissionsStep()
                Step.LOCKING -> LockingStep()
                Step.APP_DETECTION -> AppDetectionStep()
                Step.LOCATIONS -> LocationsStep(premium)
                Step.DONE -> DoneStep(faceEnrolled = hasFace)
                Step.ENROLL -> Unit
            }
        }

        val canContinue = when (step) {
            Step.PINS -> real.length in 4..6 &&
                (decoy.isBlank() || (decoy.length in 4..6 && decoy != real)) &&
                (panic.isBlank() || (panic.length in 4..6 && panic != real))
            else -> true
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (index > 0) {
                TextButton(onClick = { index-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            Button(
                onClick = {
                    // Persist PINs as soon as the user leaves the PIN step.
                    if (step == Step.PINS) viewModel.savePins(real, decoy.ifBlank { null }, panic.ifBlank { null })
                    advance()
                },
                enabled = canContinue,
                modifier = Modifier.weight(2f).height(52.dp),
            ) {
                Text(if (step == Step.DONE) "Start protecting" else "Continue")
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

private val Step.optional: Boolean
    get() = this == Step.APP_DETECTION || this == Step.LOCATIONS

@Composable
private fun StepHeroOrb(icon: ImageVector, title: String, subtitle: String) {
    StatusOrb(active = true, icon = icon, size = 150.dp)
    Spacer(Modifier.height(Spacing.lg))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Spacing.sm))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepHero(icon: ImageVector, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
    }
    Spacer(Modifier.height(Spacing.lg))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Spacing.sm))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StepContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun WelcomeStep(onShowLegal: (Int) -> Unit = {}) {
    StepContainer {
        GuardiaLogo(size = 140.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text("Welcome to Guardia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "On-device AI that locks your phone for anyone but you. Your face and voice never leave this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        FeatureLine(Icons.Filled.CameraAlt, "Face recognition", "Continuously verifies it's really you.")
        FeatureLine(Icons.Filled.Lock, "Auto-lock intruders", "Locks instantly and snaps a photo.")
        FeatureLine(Icons.Filled.VisibilityOff, "Private by design", "All biometrics stay encrypted on-device.")
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "By continuing, you agree to our",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onShowLegal(com.guardia.app.R.raw.privacy_policy) }) { Text("Privacy Policy") }
            Text("&", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onShowLegal(com.guardia.app.R.raw.terms_of_use) }) { Text("Terms of Use") }
        }
    }
}

@Composable
private fun HowItWorksStep() {
    StepContainer {
        StepHero(Icons.Filled.Bolt, "How Guardia protects you", "A few quick steps and you're covered. Here's what we'll set up together.")
        Spacer(Modifier.height(Spacing.xl))
        FeatureLine(Icons.Filled.Lock, "1. Secret PINs", "A real PIN for you, plus optional decoy and panic PINs.")
        FeatureLine(Icons.Filled.Face, "2. Your face", "Enroll a few angles so Guardia recognizes only you.")
        FeatureLine(Icons.Filled.AdminPanelSettings, "3. Locking power", "Grant the permissions that let Guardia lock instantly.")
        FeatureLine(Icons.Filled.LocationOn, "4. Smart zones", "Optionally relax checks where you feel safe (Premium).")
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(icon)
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PinStep(
    real: String, onReal: (String) -> Unit,
    decoy: String, onDecoy: (String) -> Unit,
    panic: String, onPanic: (String) -> Unit,
) {
    StepContainer {
        StepHero(Icons.Filled.Lock, "Create your PINs", "Real PIN opens Guardia. Decoy opens a harmless game. Panic is for emergencies. Each 4-6 digits.")
        Spacer(Modifier.height(Spacing.xl))
        PinField("Real PIN (required)", real, onReal)
        Spacer(Modifier.height(Spacing.md))
        PinField("Decoy PIN (optional)", decoy, onDecoy)
        Spacer(Modifier.height(Spacing.md))
        PinField("Panic PIN (optional)", panic, onPanic)
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    StepContainer {
        StepHero(Icons.Filled.CameraAlt, "Core permissions", "Guardia needs the camera to recognize faces, and notifications to show that guarding is active.")
        Spacer(Modifier.height(Spacing.xl))
        PermissionRow(
            icon = Icons.Filled.CameraAlt,
            title = "Camera",
            subtitle = "Recognize your face during checks.",
            granted = cameraGranted,
            onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        )
        Spacer(Modifier.height(Spacing.md))
        PermissionRow(
            icon = Icons.Filled.NotificationsActive,
            title = "Notifications",
            subtitle = "Show the ongoing guarding status.",
            granted = notifGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else notifGranted = true
            },
        )
    }
}

@Composable
private fun LockingStep() {
    val context = LocalContext.current
    var adminActive by remember { mutableStateOf(DeviceAdminManager.isAdminActive(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        adminActive = DeviceAdminManager.isAdminActive(context)
    }
    StepContainer {
        StepHero(Icons.Filled.AdminPanelSettings, "Enable device locking", "Device Admin lets Guardia lock the screen the instant an unauthorized person is detected. Without it, Guardia can still detect and capture — but can't lock.")
        Spacer(Modifier.height(Spacing.xl))
        PermissionRow(
            icon = Icons.Filled.AdminPanelSettings,
            title = "Device locking",
            subtitle = "Required to lock the screen.",
            granted = adminActive,
            onClick = { runCatching { launcher.launch(DeviceAdminManager.enableIntent(context)) } },
        )
    }
}

@Composable
private fun AppDetectionStep() {
    val context = LocalContext.current
    var accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        accessibilityOn = isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }
    StepContainer {
        StepHero(Icons.Filled.PhonelinkLock, "Guard specific apps", "Optional: let Guardia notice when sensitive apps open so it can require a face check first. This uses the accessibility service and stays fully on-device.")
        Spacer(Modifier.height(Spacing.xl))
        PermissionRow(
            icon = Icons.Filled.PhonelinkLock,
            title = "App detection service",
            subtitle = "Enables Check on App Open & App Lock.",
            granted = accessibilityOn,
            onClick = { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
        )
    }
}

@Composable
private fun LocationsStep(premium: Boolean) {
    val context = LocalContext.current
    var locationGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    StepContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Secure locations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(Spacing.sm))
            PremiumBadge()
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Define safe zones (like home) where checks can relax, and stay strict everywhere else. Grant location now, then fine-tune zones later in Settings › Location Protection.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        PermissionRow(
            icon = Icons.Filled.LocationOn,
            title = "Location access",
            subtitle = if (premium) "Used to detect your safe zones." else "Premium unlocks safe-zone rules.",
            granted = locationGranted,
            onClick = {
                launcher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            },
        )
    }
}

@Composable
private fun DoneStep(faceEnrolled: Boolean) {
    StepContainer {
        GuardiaLogo(size = 120.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text("You're protected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Guardia is ready. Tap Start protecting to begin guarding. You can adjust everything anytime in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        if (!faceEnrolled) {
            FeatureLine(Icons.Filled.Face, "Tip: enroll your face", "Add your face in People for the strongest protection.")
        }
        FeatureLine(Icons.Filled.Shield, "Tap Start protecting", "Begin guarding from the home screen anytime.")
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, subtitle: String? = null, granted: Boolean, onClick: () -> Unit) {
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(icon, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (granted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onClick) { Text("Enable") }
            }
        }
    }
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return flat.contains("${context.packageName}/")
}
