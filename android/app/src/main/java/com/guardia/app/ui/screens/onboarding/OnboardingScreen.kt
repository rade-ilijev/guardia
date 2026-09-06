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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.ui.components.Button
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaLogo
import com.guardia.app.ui.components.IconChip
import com.guardia.app.ui.components.LinearProgressIndicator
import com.guardia.app.ui.components.OutlinedTextField
import com.guardia.app.ui.components.PremiumBadge
import com.guardia.app.ui.components.StatusOrb
import com.guardia.app.ui.components.TextButton
import com.guardia.app.ui.screens.people.AddPersonScreen
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Spacing
import com.guardia.app.ui.components.rememberAccessibilityOptIn
import androidx.compose.foundation.border
import com.guardia.app.ui.components.glow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Block
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * The onboarding walk. Permissions first, because nothing works without them — then, before the
 * user is dropped on the dashboard, two steps that used to be missing:
 *
 *  - [Step.PROTECTION] configures the guard. The interesting options (check the instant you unlock,
 *    intruder selfies, shake-to-check, the responsiveness profile) are switched on *here*, from
 *    the step itself, rather than left for the user to discover three menus deep.
 *  - [Step.DISCOVER] shows what else the app can do — decoy PIN, guest passes, per-app face checks,
 *    trusted Wi-Fi, safe zones, alerts — as a tour, so the user knows those exist before they need
 *    them.
 */
private enum class Step {
    WELCOME, HOW, PINS, PERMISSIONS, ENROLL, LOCKING, APP_DETECTION, LOCATIONS, PROTECTION, DISCOVER, DONE
}

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
    // Whether the protection step has already applied its one-time recommendation, so going Back
    // and Forward through the walk doesn't keep re-enabling a switch the user turned off.
    var protectionSuggested by rememberSaveable { mutableStateOf(false) }
    // Non-null while the freshly minted recovery code is being shown; it is never recoverable after.
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    var showRestore by remember { mutableStateOf(false) }
    // Set when the user asks for the camera on a step that would otherwise say "already enrolled".
    // Saveable so rotating the phone mid-enrollment doesn't drop the camera.
    var enrollAnother by rememberSaveable { mutableStateOf(false) }
    val hasFace by viewModel.hasEnrolledFace.collectAsStateWithLifecycle()
    val premium by viewModel.premium.collectAsStateWithLifecycle()

    val step = steps[index]
    var legalDoc by remember { mutableStateOf<Int?>(null) }
    val advance: () -> Unit = {
        if (index < steps.lastIndex) index++ else viewModel.finish(onComplete)
    }

    recoveryCode?.let { code ->
        com.guardia.app.ui.components.RecoveryCodeDialog(code) { recoveryCode = null }
    }

    if (showRestore) {
        RestoreBackupDialog(viewModel = viewModel, onDismiss = { showRestore = false })
    }

    legalDoc?.let { rawRes ->
        com.guardia.app.ui.components.LegalDocDialog(
            title = if (rawRes == com.guardia.app.R.raw.privacy_policy) "Privacy Policy" else "Terms of Use",
            rawRes = rawRes,
            onDismiss = { legalDoc = null },
        )
    }

    // The enrollment step takes over the whole screen (it brings its own camera UI and chrome) —
    // but only when there is something to enroll. A restore on the welcome screen, or an earlier
    // pass through this step, already leaves a face on file, and re-opening the camera over it
    // would read as the restore having failed.
    if (step == Step.ENROLL && (!hasFace || enrollAnother)) {
        AddPersonScreen(onDone = { enrollAnother = false; advance() })
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
        Spacer(Modifier.height(Spacing.md))
        // Explicit brand colour: the default indicator is a hairline that all but disappears on
        // the light theme, which is the one thing on the screen telling the user how far in they
        // are.
        LinearProgressIndicator(
            progress = { (index + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = Guardia.colors.brand,
            trackColor = Guardia.colors.muted,
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
                Step.WELCOME -> WelcomeStep(
                    onShowLegal = { legalDoc = it },
                    onRestore = { showRestore = true },
                )
                Step.HOW -> HowItWorksStep()
                Step.PINS -> PinStep(real, { real = it }, decoy, { decoy = it }, panic, { panic = it })
                Step.PERMISSIONS -> PermissionsStep()
                Step.LOCKING -> LockingStep()
                Step.APP_DETECTION -> AppDetectionStep()
                Step.LOCATIONS -> LocationsStep(premium)
                Step.PROTECTION -> ProtectionStep(
                    viewModel,
                    suggested = protectionSuggested,
                    onSuggested = { protectionSuggested = true },
                )
                Step.DISCOVER -> DiscoverStep()
                Step.DONE -> DoneStep(faceEnrolled = hasFace)
                Step.ENROLL -> AlreadyEnrolledStep(onAddAnother = { enrollAnother = true })
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
                    if (step == Step.PINS) {
                        viewModel.savePins(real, decoy.ifBlank { null }, panic.ifBlank { null }) { code ->
                            recoveryCode = code
                        }
                    }
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

/**
 * Steps a user can pass without answering.
 *
 * Eleven steps is a long walk for someone who just installed a security app, and only two of them
 * used to be skippable. These four are the ones that cost nothing to miss: the two hardware
 * permissions can be granted later from the dashboard's own checklist, the protection tuning has
 * working defaults already applied, and the feature tour is a tour — it is the least urgent screen
 * in the flow and it sits at step ten, where attention has already gone.
 *
 * The steps that stay mandatory are the ones without which the app cannot do its job: the PIN, the
 * face, and the camera permission.
 */
private val Step.optional: Boolean
    get() = this == Step.APP_DETECTION ||
        this == Step.LOCATIONS ||
        this == Step.PROTECTION ||
        this == Step.DISCOVER

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
    val c = Guardia.colors
    // Each step opens on a lit brand disc rather than a gray one — the onboarding is the app's
    // first impression, and it should look like the product it is selling.
    Box(
        modifier = Modifier
            .glow(c.brand, CircleShape, radius = 28.dp, alpha = 0.35f)
            .size(96.dp)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(c.brand.copy(alpha = 0.26f), c.brand.copy(alpha = 0.06f)),
                ),
            )
            .border(1.dp, c.brand.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(44.dp))
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
    // Centred, not top-aligned. Most of these steps are shorter than the screen, and top-aligning
    // them left a third of the display empty under the content with the button stranded at the
    // bottom — the layout read as unfinished rather than spacious. Arrangement.Center inside a
    // scrollable column centres while the content fits and scrolls the moment it does not, so the
    // long steps still behave.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun WelcomeStep(onShowLegal: (Int) -> Unit = {}, onRestore: () -> Unit = {}) {
    StepContainer {
        // Brand mark lit from within by an ambient glow — the app's first impression.
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(240.dp).background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(
                            Guardia.colors.brand.copy(alpha = 0.20f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    ),
                    androidx.compose.foundation.shape.CircleShape,
                ),
            )
            GuardiaLogo(size = 140.dp)
        }
        Spacer(Modifier.height(Spacing.lg))
        // The wordmark, not "Welcome to Guardia" set in the interface font. This is the first
        // thing anyone sees of the product and the one place the brand should be unmistakable.
        com.guardia.app.ui.components.GuardiaWordmark(fontSize = 30.sp)
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
        FeatureLine(Icons.Filled.VisibilityOff, "Private by design", "Your face stays on this device and is never uploaded.")
        Spacer(Modifier.height(Spacing.lg))
        // Offered here, before anything is set up, because the point of a backup is to spare
        // someone re-enrolling every face by hand — and by the time they find this in Settings they
        // will already have done it.
        com.guardia.app.ui.components.ShButton(
            text = "Restore from a backup",
            onClick = onRestore,
            variant = com.guardia.app.ui.components.ButtonVariant.Ghost,
            size = com.guardia.app.ui.components.ButtonSize.Sm,
            leadingIcon = Icons.Filled.FolderOpen,
        )
        Spacer(Modifier.height(Spacing.sm))
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

/**
 * Stands in for the enrollment step once a face is already on file — after a restore, or after the
 * user has walked back to this step. It offers the camera rather than forcing it.
 */
@Composable
private fun AlreadyEnrolledStep(onAddAnother: () -> Unit) {
    StepContainer {
        StepHeroOrb(Icons.Filled.CheckCircle, "Your face is on file", "Guardia already has a face enrolled, so there's nothing to do here.")
        Spacer(Modifier.height(Spacing.xl))
        FeatureLine(Icons.Filled.Face, "Add another face", "Enroll a second person, or more angles of your own face.")
        Spacer(Modifier.height(Spacing.md))
        com.guardia.app.ui.components.ShButton(
            text = "Enroll a face",
            onClick = onAddAnother,
            variant = com.guardia.app.ui.components.ButtonVariant.Outline,
            leadingIcon = Icons.Filled.Face,
        )
    }
}

/**
 * Restore during setup. Merges into whatever is enrolled rather than replacing it — see
 * [OnboardingViewModel.restoreFromBackup].
 */
@Composable
private fun RestoreBackupDialog(viewModel: OnboardingViewModel, onDismiss: () -> Unit) {
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) { uri = picked; message = null }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Guardia.colors.popover,
        icon = {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = when {
                    done -> Guardia.colors.success
                    message != null -> Guardia.colors.destructive
                    else -> Guardia.colors.brand
                },
            )
        },
        title = { Text(if (done) "Restored" else "Restore from a backup", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (done) {
                    Text(
                        message ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Pick a Guardia backup file and enter the password you chose when you exported it. Your people and enrolled faces come back; PINs and settings are set up fresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    com.guardia.app.ui.components.ShButton(
                        text = if (uri == null) "Choose backup file" else "Backup file selected",
                        onClick = { picker.launch(arrayOf("*/*")) },
                        variant = com.guardia.app.ui.components.ButtonVariant.Outline,
                        leadingIcon = Icons.Filled.FolderOpen,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    com.guardia.app.ui.components.ShInput(
                        value = password,
                        onValueChange = { password = it; message = null },
                        placeholder = "Backup password",
                        enabled = !busy,
                        isError = message != null,
                        errorMessage = message,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Guardia.colors.destructiveForeground)
                    }
                }
            }
        },
        confirmButton = {
            if (done) {
                com.guardia.app.ui.components.ShButton(text = "Continue setup", onClick = onDismiss)
            } else {
                com.guardia.app.ui.components.ShButton(
                    text = if (busy) "Restoring..." else "Restore",
                    enabled = !busy && uri != null && password.isNotEmpty(),
                    onClick = {
                        val source = uri ?: return@ShButton
                        busy = true
                        message = null
                        viewModel.restoreFromBackup(source, password) { ok, text ->
                            busy = false
                            message = text
                            if (ok) { done = true; password = "" }
                        }
                    },
                )
            }
        },
        dismissButton = {
            if (!done) {
                com.guardia.app.ui.components.ShButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = com.guardia.app.ui.components.ButtonVariant.Ghost,
                    enabled = !busy,
                )
            }
        },
    )
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
        // Play's AccessibilityService policy requires a prominent disclosure before the user is
        // sent to enable the service; rememberAccessibilityOptIn shows it.
        val openAccessibility = rememberAccessibilityOptIn()
        PermissionRow(
            icon = Icons.Filled.PhonelinkLock,
            title = "App detection service",
            subtitle = "Enables Check on App Open & App Lock.",
            granted = accessibilityOn,
            onClick = openAccessibility,
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


/**
 * "Choose your protection" — the guard's most useful options, switched on from the step itself.
 *
 * Each toggle writes the preference immediately, so leaving onboarding leaves the app configured.
 * The recommended ones are on by default; the copy says what each does in one line and, where it
 * matters, what it costs.
 */
@Composable
private fun ProtectionStep(
    viewModel: OnboardingViewModel,
    suggested: Boolean,
    onSuggested: () -> Unit,
) {
    val responsiveness by viewModel.responsiveness.collectAsStateWithLifecycle()
    val firstCheck by viewModel.firstCheckOnUnlock.collectAsStateWithLifecycle()
    val capture by viewModel.captureIntruders.collectAsStateWithLifecycle()
    val shake by viewModel.shakeToCheck.collectAsStateWithLifecycle()
    val multi by viewModel.lockOnMultipleFaces.collectAsStateWithLifecycle()

    // Turn the recommended option on the first time this step is reached. It is a suggestion the
    // user can flip back, not a hidden default: the switch is right there, on.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!suggested) {
            viewModel.setFirstCheckOnUnlock(true)
            onSuggested()
        }
    }

    StepContainer {
        StepHero(
            Icons.Filled.Tune,
            "Choose your protection",
            "These are on from the moment you finish. Change any of them later in Settings.",
        )
        Spacer(Modifier.height(Spacing.xl))

        com.guardia.app.ui.components.ShSectionLabel("How often to check")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            ProfileCard("Saver", "Fewer checks, least battery", Icons.Filled.BatteryFull, responsiveness == 0, Modifier.weight(1f)) {
                viewModel.setResponsiveness(0)
            }
            ProfileCard("Balanced", "Sensor-gated checks", Icons.Filled.Shield, responsiveness == 1, Modifier.weight(1f)) {
                viewModel.setResponsiveness(1)
            }
            ProfileCard("Max", "Frequent checks", Icons.Filled.GppGood, responsiveness == 2, Modifier.weight(1f)) {
                viewModel.setResponsiveness(2)
            }
        }
        Spacer(Modifier.height(Spacing.xl))

        com.guardia.app.ui.components.ShSectionLabel("Recommended")
        com.guardia.app.ui.components.SettingsGroup {
            com.guardia.app.ui.components.SwitchRow(
                "Check the instant you unlock",
                firstCheck,
                viewModel::setFirstCheckOnUnlock,
                subtitle = "One quick look at whoever just unlocked the phone.",
                leading = Icons.Filled.LockOpen,
            )
            com.guardia.app.ui.components.RowDivider()
            com.guardia.app.ui.components.SwitchRow(
                "Capture intruder selfies",
                capture,
                viewModel::setCaptureIntruders,
                subtitle = "An encrypted photo of whoever triggers a lock, kept on this phone.",
                leading = Icons.Filled.PhotoCamera,
            )
            com.guardia.app.ui.components.RowDivider()
            com.guardia.app.ui.components.SwitchRow(
                "Lock when two faces are seen",
                multi,
                viewModel::setLockOnMultipleFaces,
                subtitle = "Someone looking over your shoulder counts.",
                leading = Icons.Filled.Groups,
            )
        }
        Spacer(Modifier.height(Spacing.lg))

        com.guardia.app.ui.components.ShSectionLabel("Nice to have")
        com.guardia.app.ui.components.SettingsGroup {
            com.guardia.app.ui.components.SwitchRow(
                "Shake to check",
                shake,
                viewModel::setShakeToCheck,
                subtitle = "Give the phone a shake to run a check right now.",
                leading = Icons.Filled.Vibration,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** One of the three responsiveness profiles, as a selectable tile. */
@Composable
private fun ProfileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Guardia.colors
    com.guardia.app.ui.components.ShCard(
        modifier = modifier,
        onClick = onClick,
        borderColor = if (selected) c.brand.copy(alpha = 0.7f) else null,
        glowColor = if (selected) c.brand else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconChip(icon, tint = if (selected) c.brand else c.mutedForeground, size = 34.dp)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) c.brand else c.foreground,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = c.mutedForeground,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/**
 * "What else Guardia can do" — a tour of the features that need a moment of setup, so the user
 * knows they exist before they need them. Each card says where to find it.
 */
@Composable
private fun DiscoverStep() {
    StepContainer {
        StepHero(
            Icons.Filled.AutoAwesome,
            "There's more when you want it",
            "Everything below is included. Set any of it up from Settings whenever it's useful.",
        )
        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            DiscoverCard(
                Icons.Filled.Calculate, Guardia.colors.violet,
                "Decoy PIN",
                "A second PIN that opens a harmless calculator instead of Guardia — for when someone insists you unlock it.",
                "Settings › PINs",
            )
            DiscoverCard(
                Icons.Filled.PhonelinkLock, Guardia.colors.brand,
                "Face check for apps",
                "Pick apps that must see your face before they open — banking, messages, photos.",
                "Settings › Face Check for Apps",
            )
            DiscoverCard(
                Icons.Filled.Schedule, Guardia.colors.success,
                "Guest pass",
                "Hand your phone to a friend for an hour without turning guarding off. Their face is trusted until the pass expires.",
                "People › Guest pass",
            )
            DiscoverCard(
                Icons.Filled.Wifi, Guardia.colors.info,
                "Relax on trusted Wi-Fi",
                "At home on your own network, checks can ease off. Anywhere else, they stay strict.",
                "Settings › Check Schedule",
            )
            DiscoverCard(
                Icons.Filled.LocationOn, Guardia.colors.warning,
                "Safe zones",
                "Guard differently at home, at work and out — down to a custom check interval per place.",
                "Settings › Location Rules",
            )
            DiscoverCard(
                Icons.Filled.Block, Guardia.colors.destructive,
                "Blocked people",
                "Enroll a face that should always lock the phone, even when it looks a lot like yours.",
                "People › Blocked",
            )
            DiscoverCard(
                Icons.Filled.NotificationsActive, Guardia.colors.warning,
                "Alerts & find my phone",
                "An email with the intruder photo and location the moment a lock happens.",
                "Settings › Alerts",
            )
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun DiscoverCard(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, body: String, where: String) {
    val c = Guardia.colors
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.Top) {
            IconChip(icon, tint = tint, size = 40.dp)
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.foreground)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
                Spacer(Modifier.height(Spacing.sm))
                com.guardia.app.ui.components.ShBadge(where, variant = com.guardia.app.ui.components.BadgeVariant.Secondary)
            }
        }
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
            IconChip(icon, tint = if (granted) Guardia.colors.success else Guardia.colors.mutedForeground)
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (granted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = Guardia.colors.success)
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
